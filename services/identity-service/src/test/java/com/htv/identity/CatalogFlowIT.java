package com.htv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.contract.identity.v1.PermissionCatalogServiceGrpc;
import com.htv.contract.identity.v1.RegisterRequest;
import com.htv.contract.identity.v1.RegisterResponse;
import com.htv.identity.application.usecase.ChangeRolePermissionsUseCase;
import com.htv.identity.application.usecase.ChangeUserRolesUseCase;
import com.htv.identity.application.usecase.GetUserPermissionsUseCase;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Command;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Definition;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Result;
import com.htv.identity.domain.model.authorization.RoleEntity;
import com.htv.identity.domain.model.identity.UserEntity;
import com.htv.shared.auth.PermissionCacheKeys;
import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.CommonErrorCode;
import com.htv.starter.security.PermissionCatalogSyncer;
import com.htv.starter.security.RequirePermissionScanner;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import com.htv.shared.common.BusinessException;
import com.htv.starter.grpc.GrpcServerLifecycle;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Đồng bộ danh mục quyền (thiết kế mục 3.5) trên PostgreSQL và Redis thật. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.password=unused",
        "htv.grpc.server.port=0",
        "htv.identity.catalog.trust-request-body=true",
        "htv.security.catalog.enabled=false"})
class CatalogFlowIT extends ContainersSupport {

    @Autowired
    RegisterPermissionCatalogUseCase register;
    @Autowired
    ChangeRolePermissionsUseCase rolePermissions;
    @Autowired
    ChangeUserRolesUseCase userRoles;
    @Autowired
    GetUserPermissionsUseCase getPermissions;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    EntityManager em;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    GrpcServerLifecycle grpc;
    @Autowired
    PermissionCatalogPublisher localPublisher;
    @Autowired
    ConfigurableListableBeanFactory beanFactory;
    @Autowired
    ObjectProvider<PermissionProvider> permissionProvider;

    // -------------------------------------------------------------------- tiện ích

    /** Tên service và tiền tố mã duy nhất cho mỗi test, để các test không đụng nhau. */
    private static String svc() {
        return "s" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Command cmd(String service, String version, String... codesAndDescriptions) {
        List<Definition> defs = new ArrayList<>();
        for (String c : codesAndDescriptions) {
            String[] parts = c.split("=", 2);
            defs.add(new Definition(parts[0], parts.length > 1 ? parts[1] : ""));
        }
        return new Command(service, version, "inst-1", defs);
    }

    private PermissionRow permission(String code) {
        return tx.execute(s -> {
            List<?> rows = em.createNativeQuery(
                    "select id, service_name, active, description from iam_permission where code = :c")
                    .setParameter("c", code).getResultList();
            if (rows.isEmpty()) {
                return null;
            }
            Object[] r = (Object[]) rows.get(0);
            return new PermissionRow((UUID) r[0], (String) r[1], (Boolean) r[2], (String) r[3]);
        });
    }

    record PermissionRow(UUID id, String service, boolean active, String description) {
    }

    private String registeredVersion(String service) {
        return tx.execute(s -> (String) em.createNativeQuery(
                "select service_version from iam_service_catalog where service_name = :n")
                .setParameter("n", service).getSingleResult());
    }

    private long auditCount(String service) {
        return tx.execute(s -> ((Number) em.createNativeQuery("select count(*) from iam_audit_log "
                        + "where action = 'PERMISSION_CATALOG_REGISTERED' and entity_id = :n")
                .setParameter("n", service).getSingleResult()).longValue());
    }

    private long count(String sql, String name, Object value) {
        Long result = tx.execute(st -> ((Number) em.createNativeQuery(sql).setParameter(name, value)
                .getSingleResult()).longValue());
        return result;
    }

    private long rolePermissionCount() {
        return tx.execute(s -> ((Number) em.createNativeQuery("select count(*) from iam_role_permission")
                .getSingleResult()).longValue());
    }

    private UUID user() {
        return tx.execute(s -> {
            String n = "u" + UUID.randomUUID();
            UserEntity u = UserEntity.register(n, n + "@example.com", null, null);
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

    // ------------------------------------------------------------------- các test

    @Test
    void firstRegistrationCreatesCodesWithOwnerAndAudit() {
        String s = svc();
        Result r = register.execute(cmd(s, "1.0.0", s + ":read=Read things", s + ":write"));

        assertThat(r.created()).isEqualTo(2);
        assertThat(r.updated()).isZero();
        assertThat(r.unchanged()).isZero();
        assertThat(r.rejected()).isEmpty();
        PermissionRow read = permission(s + ":read");
        assertThat(read.service()).isEqualTo(s);
        assertThat(read.active()).isTrue();
        assertThat(read.description()).isEqualTo("Read things");
        assertThat(registeredVersion(s)).isEqualTo("1.0.0");
        assertThat(auditCount(s)).isEqualTo(1);
    }

    @Test
    void registeringAgainChangesNothingAndWritesNoNewAudit() {
        String s = svc();
        Command c = cmd(s, "1.0.0", s + ":read=Read", s + ":write");
        register.execute(c);
        long audits = auditCount(s);

        Result again = register.execute(c);

        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(again.unchanged()).isEqualTo(2);
        assertThat(again.deactivated()).isZero();
        assertThat(auditCount(s)).isEqualTo(audits);
    }

    @Test
    void newCodesAreNeverGrantedToAnyRole() {
        long before = rolePermissionCount();
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":read", s + ":write"));
        assertThat(rolePermissionCount()).isEqualTo(before);
    }

    @Test
    void changedDescriptionIsAnUpdateAndEmptyDescriptionKeepsTheOldOne() {
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":read=Old"));
        assertThat(register.execute(cmd(s, "1.0.1", s + ":read=New")).updated()).isEqualTo(1);
        assertThat(permission(s + ":read").description()).isEqualTo("New");
        assertThat(register.execute(cmd(s, "1.0.2", s + ":read")).unchanged()).isEqualTo(1);
        assertThat(permission(s + ":read").description()).isEqualTo("New");
    }

    @Test
    void codeRemovedFromCodeIsDeactivatedNotDeletedAndRoleLinkSurvives() {
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":read", s + ":write"));
        UUID write = permission(s + ":write").id();
        UUID role = role();
        UUID user = user();
        rolePermissions.grant(role, permission(s + ":read").id());
        rolePermissions.grant(role, write);
        userRoles.assign(user, role, null, null, null);
        assertThat(getPermissions.execute(user)).containsExactlyInAnyOrder(s + ":read", s + ":write");
        assertThat(redis.opsForSet().members(PermissionCacheKeys.userKey(user))).isNotEmpty();

        Result r = register.execute(cmd(s, "1.1.0", s + ":read"));

        assertThat(r.deactivated()).isEqualTo(1);
        PermissionRow gone = permission(s + ":write");
        assertThat(gone).isNotNull();
        assertThat(gone.active()).isFalse();
        // liên kết với role còn nguyên: mở lại được
        assertThat(count("select count(*) from iam_role_permission where permission_id = :p", "p", write))
                .isEqualTo(1);
        // người dùng giữ quyền đó bị xóa cache và không còn thấy quyền đã tắt
        assertThat(redis.opsForSet().members(PermissionCacheKeys.userKey(user))).isEmpty();
        assertThat(getPermissions.execute(user)).containsExactly(s + ":read");
        assertThat(registeredVersion(s)).isEqualTo("1.1.0");
    }

    @Test
    void codeComingBackIsReactivatedWithItsRoleLinkAndCacheIsEvicted() {
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":read", s + ":write"));
        UUID role = role();
        UUID user = user();
        rolePermissions.grant(role, permission(s + ":write").id());
        userRoles.assign(user, role, null, null, null);
        register.execute(cmd(s, "1.1.0", s + ":read"));
        assertThat(getPermissions.execute(user)).isEmpty();
        assertThat(redis.opsForSet().members(PermissionCacheKeys.userKey(user))).isNotEmpty();

        register.execute(cmd(s, "1.2.0", s + ":read", s + ":write"));

        assertThat(permission(s + ":write").active()).isTrue();
        assertThat(redis.opsForSet().members(PermissionCacheKeys.userKey(user))).isEmpty();
        assertThat(getPermissions.execute(user)).containsExactly(s + ":write");
    }

    @Test
    void olderInstanceDoesNotDeactivateAndDoesNotRollTheVersionBack() {
        String s = svc();
        register.execute(cmd(s, "2.0.0", s + ":read", s + ":new-feature"));

        Result old = register.execute(cmd(s, "1.9.0", s + ":read"));

        assertThat(old.deactivated()).isZero();
        assertThat(permission(s + ":new-feature").active()).isTrue();
        assertThat(registeredVersion(s)).isEqualTo("2.0.0");
    }

    @Test
    void olderInstanceStillReactivatesCodesInItsOwnList() {
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":read", s + ":legacy"));
        register.execute(cmd(s, "2.0.0", s + ":read")); // legacy bị tắt
        assertThat(permission(s + ":legacy").active()).isFalse();

        register.execute(cmd(s, "1.5.0", s + ":read", s + ":legacy")); // rollback: bản cũ còn dùng legacy

        assertThat(permission(s + ":legacy").active()).isTrue();
        assertThat(registeredVersion(s)).isEqualTo("2.0.0");
    }

    @Test
    void aCodeOwnedByAnotherServiceIsRejectedAloneAndTheRestStillGoThrough() {
        String owner = svc();
        String other = svc();
        register.execute(cmd(owner, "1.0.0", owner + ":shared"));

        Result r = register.execute(cmd(other, "1.0.0", owner + ":shared", other + ":own"));

        assertThat(r.rejected()).extracting("code", "reason")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(owner + ":shared", "OWNED_BY_OTHER"));
        assertThat(r.created()).isEqualTo(1);
        assertThat(permission(owner + ":shared").service()).isEqualTo(owner);
        assertThat(permission(other + ":own").service()).isEqualTo(other);
    }

    @Test
    void otherServiceCannotDeactivateCodesItDoesNotOwn() {
        String owner = svc();
        String attacker = svc();
        register.execute(cmd(owner, "1.0.0", owner + ":read"));
        register.execute(cmd(attacker, "9.9.9")); // danh sách rỗng
        assertThat(permission(owner + ":read").active()).isTrue();
    }

    @Test
    void invalidCodesAreRejectedWithoutAffectingValidOnes() {
        String s = svc();
        Result r = register.execute(cmd(s, "1.0.0", "Bad Code", "*", s + ":*", s + ":ok"));
        assertThat(r.rejected()).extracting("code").containsExactlyInAnyOrder("Bad Code", "*", s + ":*");
        assertThat(r.rejected()).extracting("reason").containsOnly("INVALID_FORMAT");
        assertThat(r.created()).isEqualTo(1);
        assertThat(permission(s + ":ok")).isNotNull();
        assertThat(permission("Bad Code")).isNull();
    }

    @Test
    void duplicateCodesInOneRequestAreMerged() {
        String s = svc();
        Result r = register.execute(cmd(s, "1.0.0", s + ":read", s + ":read=Described"));
        assertThat(r.created()).isEqualTo(1);
        assertThat(permission(s + ":read").description()).isEqualTo("Described");
    }

    @Test
    void emptyListFromAServiceDeactivatesOnlyItsOwnCodes() {
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":a", s + ":b"));
        Result r = register.execute(cmd(s, "1.0.1"));
        assertThat(r.deactivated()).isEqualTo(2);
    }

    @Test
    void invalidServiceNameIsRejected() {
        assertThatThrownBy(() -> register.execute(cmd("bad name!", "1.0.0")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> register.execute(cmd(" ", "1.0.0"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void concurrentInstancesGetTheSameResultWithoutDuplicates() throws Exception {
        String s = svc();
        Command c = cmd(s, "1.0.0", s + ":a", s + ":b", s + ":c");
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Result> task = () -> {
                    go.await();
                    return register.execute(c);
                };
                futures.add(pool.submit(task));
            }
            go.countDown();
            int created = 0;
            int unchanged = 0;
            for (Future<Result> f : futures) {
                Result r = f.get(30, TimeUnit.SECONDS);
                created += r.created();
                unchanged += r.unchanged();
                assertThat(r.rejected()).isEmpty();
            }
            assertThat(created).isEqualTo(3); // chỉ một instance tạo
            assertThat(unchanged).isEqualTo(3 * (threads - 1));
            assertThat(count("select count(*) from iam_permission where service_name = :n", "n", s)).isEqualTo(3);
            assertThat(count("select count(*) from iam_service_catalog where service_name = :n", "n", s))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------- qua gRPC

    @Test
    void grpcRegisterWorksWithBodyNameInDevMode() {
        String s = svc();
        ManagedChannel ch = Grpc.newChannelBuilder("localhost:" + grpc.getPort(), InsecureChannelCredentials.create())
                .build();
        try {
            var stub = PermissionCatalogServiceGrpc.newBlockingStub(ch);
            RegisterResponse resp = stub.register(RegisterRequest.newBuilder().setServiceName(s)
                    .setServiceVersion("1.0.0").setInstanceId("i")
                    .addPermissions(com.htv.contract.identity.v1.PermissionDef.newBuilder().setCode(s + ":read").setDescription("Read"))
                    .addPermissions(com.htv.contract.identity.v1.PermissionDef.newBuilder().setCode("Bad Code")).build());
            assertThat(resp.getCreated()).isEqualTo(1);
            assertThat(resp.getRejectedList()).extracting("code", "reason")
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("Bad Code", "INVALID_FORMAT"));
            assertThat(permission(s + ":read")).isNotNull();

            assertThatThrownBy(() -> stub.register(RegisterRequest.newBuilder().setServiceVersion("1").build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
        } finally {
            ch.shutdownNow();
        }
    }

    @Test
    void emptyPermissionSetIsStillReturnedForUsersOfDeactivatedOnlyRoles() {
        // người dùng chỉ có quyền đã bị tắt: tập rỗng được cache bằng dấu "không có quyền"
        String s = svc();
        register.execute(cmd(s, "1.0.0", s + ":only"));
        UUID role = role();
        UUID user = user();
        rolePermissions.grant(role, permission(s + ":only").id());
        userRoles.assign(user, role, null, null, null);
        assertThat(getPermissions.execute(user)).containsExactly(s + ":only");

        register.execute(cmd(s, "2.0.0"));

        assertThat(getPermissions.execute(user)).isEmpty();
        assertThat(redis.opsForSet().members(PermissionCacheKeys.userKey(user)))
                .isEqualTo(Set.of(PermissionCacheKeys.NONE));
    }

    // ------------------------------------------------- identity chưa sẵn sàng lúc service khởi động

    @Test
    void syncerRetriesWhileIdentityIsDownAndRegistersOnceItIsUp() throws Exception {
        String s = svc();
        AtomicInteger calls = new AtomicInteger();
        PermissionCatalogPublisher flaky = catalog -> {
            if (calls.incrementAndGet() <= 2) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
            }
            return localPublisher.publish(catalog);
        };
        RequirePermissionScanner scanner = new RequirePermissionScanner(beanFactory, permissionProvider) {
            @Override
            public List<PermissionDef> declared() {
                return List.of(new PermissionDef(s + ":read", "Read"));
            }
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        PermissionCatalogSyncer syncer = new PermissionCatalogSyncer(scanner, flaky, executor, s, "1.0.0", "i-1",
                false, Duration.ofMillis(50), Duration.ofMillis(100));
        try {
            syncer.start(); // không chặn: trả về ngay dù identity "chưa sẵn sàng"
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (permission(s + ":read") == null && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertThat(permission(s + ":read")).isNotNull();
            assertThat(calls).hasValue(3);
            Thread.sleep(300);
            assertThat(calls).as("không thử lại sau khi đã thành công").hasValue(3);
        } finally {
            syncer.destroy();
        }
    }
}
