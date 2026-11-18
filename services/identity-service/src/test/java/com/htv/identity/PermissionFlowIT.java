package com.htv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.identity.application.usecase.ChangeRolePermissionsUseCase;
import com.htv.identity.application.usecase.ChangeUserRolesUseCase;
import com.htv.identity.domain.enums.UserStatus;
import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.RoleEntity;
import com.htv.identity.domain.model.identity.UserEntity;
import com.htv.shared.auth.PermissionCacheKeys;
import com.htv.shared.auth.PermissionProvider;
import com.htv.starter.cached.RedisPermissionProvider;
import com.htv.starter.grpc.GrpcMetadataKeys;
import com.htv.starter.grpc.GrpcServerLifecycle;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Luồng quyền đầu-cuối trên PostgreSQL và Redis thật, qua gRPC thật: tính quyền, ghi cache,
 * và xóa cache sau khi đổi role/quyền.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.password=unused",
        "htv.grpc.server.port=0"})
class PermissionFlowIT extends ContainersSupport {

    @Autowired
    TransactionTemplate tx;
    @Autowired
    EntityManager em;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    GrpcServerLifecycle grpc;
    @Autowired
    ChangeRolePermissionsUseCase rolePermissions;
    @Autowired
    ChangeUserRolesUseCase userRoles;
    @Autowired
    PermissionProvider identityProvider;

    // ----------------------------------------------------------------- dữ liệu

    private static String code() {
        return "t" + UUID.randomUUID().toString().substring(0, 8) + ":read";
    }

    private UUID user(UserStatus status) {
        return tx.execute(s -> {
            String name = "u" + UUID.randomUUID();
            UserEntity u = UserEntity.register(name, name + "@example.com", null, null);
            u.changeStatus(status);
            em.persist(u);
            em.flush();
            return u.getId();
        });
    }

    private UUID role() {
        return tx.execute(s -> {
            RoleEntity r = RoleEntity.create("R" + UUID.randomUUID(), "Role", null);
            em.persist(r);
            em.flush();
            return r.getId();
        });
    }

    private UUID permission(String code, boolean active) {
        return tx.execute(s -> {
            PermissionEntity p = PermissionEntity.declare(code, null, "it");
            if (!active) {
                p.deactivate();
            }
            em.persist(p);
            em.flush();
            return p.getId();
        });
    }

    private ManagedChannel channel() {
        return Grpc.newChannelBuilder("localhost:" + grpc.getPort(), InsecureChannelCredentials.create()).build();
    }

    private Set<String> askIdentity(UUID user) {
        ManagedChannel ch = channel();
        try {
            return Set.copyOf(PermissionQueryServiceGrpc.newBlockingStub(ch)
                    .getUserPermissions(GetUserPermissionsRequest.newBuilder().setUserId(user.toString()).build())
                    .getPermissionsList());
        } finally {
            ch.shutdownNow();
        }
    }

    private Set<String> cached(UUID user) {
        return redis.opsForSet().members(PermissionCacheKeys.userKey(user));
    }

    // ------------------------------------------------------------------- tính quyền

    @Test
    void grpcReturnsOnlyEffectiveActivePermissionsAndWritesTheCache() {
        UUID user = user(UserStatus.ACTIVE);
        UUID current = role();
        UUID expired = role();
        UUID future = role();
        String codeCurrent = code(), codeInactive = code(), codeExpired = code(), codeFuture = code();
        rolePermissions.grant(current, permission(codeCurrent, true));
        rolePermissions.grant(current, permission(codeInactive, false));
        rolePermissions.grant(expired, permission(codeExpired, true));
        rolePermissions.grant(future, permission(codeFuture, true));
        Instant now = Instant.now();
        userRoles.assign(user, current, now.minusSeconds(60), null, null);
        userRoles.assign(user, expired, now.minusSeconds(3600), now.minusSeconds(60), null);
        userRoles.assign(user, future, now.plusSeconds(3600), null, null);

        assertThat(askIdentity(user)).containsExactly(codeCurrent);

        assertThat(cached(user)).containsExactly(codeCurrent);
        Long ttl = redis.getExpire(PermissionCacheKeys.userKey(user));
        assertThat(ttl).isBetween(1L, 600L);
    }

    @Test
    void whatIdentityWritesTheOtherServicesCanRead() {
        UUID user = user(UserStatus.ACTIVE);
        UUID role = role();
        String c = code();
        rolePermissions.grant(role, permission(c, true));
        userRoles.assign(user, role, null, null, null);
        askIdentity(user);

        // phía service: chỉ đọc Redis, không có identity để hỏi
        assertThat(new RedisPermissionProvider(redis, null).permissionsOf(user)).containsExactly(c);
    }

    @Test
    void wildcardPermissionIsReturnedAsIs() {
        UUID user = user(UserStatus.ACTIVE);
        UUID role = role();
        UUID superPerm = tx.execute(s -> (UUID) em.createQuery("select p.id from PermissionEntity p where p.code = '*'",
                UUID.class).getSingleResult());
        rolePermissions.grant(role, superPerm);
        userRoles.assign(user, role, null, null, null);
        assertThat(askIdentity(user)).containsExactly("*");
    }

    @Test
    void lockedUserHasNoPermissionsAndEmptyIsCachedAsNoneMarker() {
        UUID user = user(UserStatus.LOCKED);
        UUID role = role();
        rolePermissions.grant(role, permission(code(), true));
        userRoles.assign(user, role, null, null, null);

        assertThat(askIdentity(user)).isEmpty();
        assertThat(cached(user)).containsExactly(PermissionCacheKeys.NONE);
    }

    @Test
    void unknownUserIsNotFoundAndInvalidIdIsRejected() {
        ManagedChannel ch = channel();
        try {
            var stub = PermissionQueryServiceGrpc.newBlockingStub(ch);
            assertThatThrownBy(() -> stub.getUserPermissions(GetUserPermissionsRequest.newBuilder()
                    .setUserId(UUID.randomUUID().toString()).build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("IDENTITY-4041");
                    });
            assertThatThrownBy(() -> stub.getUserPermissions(GetUserPermissionsRequest.newBuilder()
                    .setUserId("not-a-uuid").build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                        assertThat(e.getTrailers().get(GrpcMetadataKeys.ERROR_CODE)).isEqualTo("COMMON-4000");
                    });
        } finally {
            ch.shutdownNow();
        }
    }

    @Test
    void identityOwnProviderTreatsUnknownUserAsNoPermissions() {
        assertThat(identityProvider.permissionsOf(UUID.randomUUID())).isEmpty();
    }

    // ----------------------------------------------------------------- xóa cache

    @Test
    void grantingAPermissionToARoleEvictsEveryMemberAndTheNextLookupSeesIt() {
        UUID role = role();
        UUID a = user(UserStatus.ACTIVE);
        UUID b = user(UserStatus.ACTIVE);
        UUID outsider = user(UserStatus.ACTIVE);
        String first = code(), second = code();
        rolePermissions.grant(role, permission(first, true));
        userRoles.assign(a, role, null, null, null);
        userRoles.assign(b, role, null, null, null);
        assertThat(askIdentity(a)).containsExactly(first);
        assertThat(askIdentity(b)).containsExactly(first);
        askIdentity(outsider);
        assertThat(cached(a)).isNotEmpty();

        rolePermissions.grant(role, permission(second, true));

        assertThat(cached(a)).isEmpty();
        assertThat(cached(b)).isEmpty();
        assertThat(cached(outsider)).as("người không thuộc role giữ nguyên cache").isNotEmpty();
        assertThat(askIdentity(a)).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void revokingEvictsAndRemovesThePermission() {
        UUID role = role();
        UUID user = user(UserStatus.ACTIVE);
        String c = code();
        UUID perm = permission(c, true);
        rolePermissions.grant(role, perm);
        userRoles.assign(user, role, null, null, null);
        assertThat(askIdentity(user)).containsExactly(c);

        rolePermissions.revoke(role, perm);

        assertThat(cached(user)).isEmpty();
        assertThat(askIdentity(user)).isEmpty();
        rolePermissions.revoke(role, perm); // idempotent
    }

    @Test
    void assigningAndEndingARoleEvictTheUsersCache() {
        UUID role = role();
        UUID user = user(UserStatus.ACTIVE);
        String c = code();
        rolePermissions.grant(role, permission(c, true));
        assertThat(askIdentity(user)).isEmpty();
        assertThat(cached(user)).containsExactly(PermissionCacheKeys.NONE);

        UUID assignment = userRoles.assign(user, role, null, null, null);
        assertThat(cached(user)).isEmpty();
        assertThat(askIdentity(user)).containsExactly(c);

        userRoles.end(assignment, Instant.now().plusMillis(1));
        assertThat(cached(user)).isEmpty();
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(askIdentity(user)).isEmpty();
    }

    @Test
    void rolledBackChangeDoesNotEvictOrChangeAnything() {
        UUID role = role();
        UUID user = user(UserStatus.ACTIVE);
        String first = code();
        rolePermissions.grant(role, permission(first, true));
        userRoles.assign(user, role, null, null, null);
        assertThat(askIdentity(user)).containsExactly(first);
        UUID extra = permission(code(), true);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            rolePermissions.grant(role, extra);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cached(user)).containsExactly(first);
        assertThat(askIdentity(user)).containsExactly(first);
    }

    @Test
    void ttlBoundsStalenessWhenEvictionIsMissed() {
        UUID user = user(UserStatus.ACTIVE);
        askIdentity(user);
        Long ttl = redis.getExpire(PermissionCacheKeys.userKey(user));
        assertThat(ttl).isPositive();
        assertThat(Duration.ofSeconds(ttl)).isLessThanOrEqualTo(Duration.ofMinutes(10));
    }
}
