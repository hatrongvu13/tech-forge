package com.htv.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.htv.identity.application.port.PermissionCache;
import com.htv.identity.application.usecase.ChangeRolePermissionsUseCase;
import com.htv.identity.application.usecase.ChangeUserRolesUseCase;
import com.htv.identity.application.usecase.GetUserPermissionsUseCase;
import com.htv.identity.domain.IdentityErrorCode;
import com.htv.identity.domain.enums.UserStatus;
import com.htv.identity.domain.event.RolePermissionsChanged;
import com.htv.identity.domain.event.UserRolesChanged;
import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import com.htv.identity.domain.repository.IamReferenceRepository;
import com.htv.identity.domain.repository.PermissionQueryRepository;
import com.htv.identity.domain.repository.RolePermissionRepository;
import com.htv.identity.domain.repository.UserRoleAssignmentRepository;
import com.htv.shared.common.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Luật của tầng application, dùng đối tượng giả; không cần DB. */
class ApplicationRulesTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ROLE = UUID.randomUUID();
    private static final UUID PERM = UUID.randomUUID();

    // -------------------------------------------------------- GetUserPermissions

    private final PermissionQueryRepository queries = mock(PermissionQueryRepository.class);
    private final PermissionCache cache = mock(PermissionCache.class);
    private final GetUserPermissionsUseCase getPermissions = new GetUserPermissionsUseCase(queries, cache, CLOCK);

    @Test
    void activeUserGetsPermissionsAndTheyAreCached() {
        when(queries.findUserStatus(USER)).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(queries.findEffectivePermissionCodes(USER, NOW)).thenReturn(Set.of("order:read", "billing:*"));
        assertThat(getPermissions.execute(USER)).containsExactlyInAnyOrder("order:read", "billing:*");
        verify(cache).put(USER, Set.of("order:read", "billing:*"));
    }

    @Test
    void nonActiveUsersHaveNoPermissionsAndEmptyIsCachedToo() {
        for (UserStatus status : new UserStatus[] {UserStatus.LOCKED, UserStatus.INACTIVE, UserStatus.DELETED}) {
            when(queries.findUserStatus(USER)).thenReturn(Optional.of(status));
            assertThat(getPermissions.execute(USER)).as(status.name()).isEmpty();
        }
        verify(queries, never()).findEffectivePermissionCodes(any(), any());
        verify(cache, org.mockito.Mockito.times(3)).put(USER, Set.of());
    }

    @Test
    void unknownUserIsNotFoundAndNothingIsCached() {
        when(queries.findUserStatus(USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> getPermissions.execute(USER)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.USER_NOT_FOUND));
        verify(cache, never()).put(any(), any());
    }

    @Test
    void cacheWriteFailureDoesNotFailTheLookup() {
        when(queries.findUserStatus(USER)).thenReturn(Optional.of(UserStatus.ACTIVE));
        when(queries.findEffectivePermissionCodes(USER, NOW)).thenReturn(Set.of("a:b"));
        doThrow(new IllegalStateException("redis down")).when(cache).put(any(), any());
        assertThat(getPermissions.execute(USER)).containsExactly("a:b");
    }

    // ---------------------------------------------------------------- SingleFlight

    @Test
    void singleFlightRunsOnceForConcurrentCallsOfTheSameKey() throws Exception {
        SingleFlight<String, Integer> sf = new SingleFlight<>();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Future<Integer> first = pool.submit(() -> sf.run("k", () -> {
                runs.incrementAndGet();
                started.countDown();
                await(release);
                return 42;
            }));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = pool.submit(() -> sf.run("k", () -> {
                runs.incrementAndGet();
                return -1;
            }));
            Future<Integer> third = pool.submit(() -> sf.run("k", () -> {
                runs.incrementAndGet();
                return -1;
            }));
            Thread.sleep(150);
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(third.get(2, TimeUnit.SECONDS)).isEqualTo(42);
            assertThat(runs).hasValue(1);
            // sau khi xong, lời gọi mới tính lại
            assertThat(sf.run("k", () -> 7)).isEqualTo(7);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void singleFlightPropagatesFailureToAllWaitersAndCleansUp() throws Exception {
        SingleFlight<String, Integer> sf = new SingleFlight<>();
        assertThatThrownBy(() -> sf.run("k", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(sf.run("k", () -> 1)).isEqualTo(1); // khóa đã được dọn
    }

    private static void await(CountDownLatch l) {
        try {
            l.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------- ChangeRolePermissions

    private final RolePermissionRepository rolePermissions = mock(RolePermissionRepository.class);
    private final IamReferenceRepository refs = mock(IamReferenceRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ChangeRolePermissionsUseCase changeRolePermissions =
            new ChangeRolePermissionsUseCase(rolePermissions, refs, events);

    private void rolesAndPermsExist() {
        when(refs.roleExists(ROLE)).thenReturn(true);
        when(refs.permissionExists(PERM)).thenReturn(true);
    }

    @Test
    void grantSavesAndPublishesOnlyWhenNew() {
        rolesAndPermsExist();
        when(rolePermissions.exists(ROLE, PERM)).thenReturn(false);
        changeRolePermissions.grant(ROLE, PERM);
        verify(rolePermissions).save(any());
        verify(events).publishEvent(new RolePermissionsChanged(ROLE));
    }

    @Test
    void grantIsIdempotent() {
        rolesAndPermsExist();
        when(rolePermissions.exists(ROLE, PERM)).thenReturn(true);
        changeRolePermissions.grant(ROLE, PERM);
        verify(rolePermissions, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void revokePublishesOnlyWhenSomethingWasRemoved() {
        rolesAndPermsExist();
        when(rolePermissions.delete(ROLE, PERM)).thenReturn(1);
        changeRolePermissions.revoke(ROLE, PERM);
        verify(events).publishEvent(new RolePermissionsChanged(ROLE));

        ApplicationEventPublisher quiet = mock(ApplicationEventPublisher.class);
        RolePermissionRepository none = mock(RolePermissionRepository.class);
        when(none.delete(ROLE, PERM)).thenReturn(0);
        new ChangeRolePermissionsUseCase(none, refs, quiet).revoke(ROLE, PERM);
        verify(quiet, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownRoleOrPermissionIsRejected() {
        when(refs.roleExists(ROLE)).thenReturn(false);
        assertThatThrownBy(() -> changeRolePermissions.grant(ROLE, PERM)).isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.ROLE_NOT_FOUND));
        when(refs.roleExists(ROLE)).thenReturn(true);
        when(refs.permissionExists(PERM)).thenReturn(false);
        assertThatThrownBy(() -> changeRolePermissions.revoke(ROLE, PERM)).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.PERMISSION_NOT_FOUND));
    }

    // ------------------------------------------------------------ ChangeUserRoles

    private final UserRoleAssignmentRepository assignments = mock(UserRoleAssignmentRepository.class);
    private final ChangeUserRolesUseCase changeUserRoles =
            new ChangeUserRolesUseCase(assignments, refs, events, CLOCK);

    @Test
    void assignDefaultsStartToNowAndPublishes() {
        when(refs.userExists(USER)).thenReturn(true);
        when(refs.roleExists(ROLE)).thenReturn(true);
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));
        changeUserRoles.assign(USER, ROLE, null, null, null);
        ArgumentCaptor<UserRoleAssignmentEntity> saved = ArgumentCaptor.forClass(UserRoleAssignmentEntity.class);
        verify(assignments).save(saved.capture());
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(NOW);
        assertThat(saved.getValue().getEffectiveTo()).isNull();
        verify(events).publishEvent(new UserRolesChanged(USER));
    }

    @Test
    void assignRejectsUnknownUserRoleAndBadPeriod() {
        when(refs.userExists(USER)).thenReturn(false);
        assertThatThrownBy(() -> changeUserRoles.assign(USER, ROLE, null, null, null)).isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.USER_NOT_FOUND));
        when(refs.userExists(USER)).thenReturn(true);
        when(refs.roleExists(ROLE)).thenReturn(false);
        assertThatThrownBy(() -> changeUserRoles.assign(USER, ROLE, null, null, null)).isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.ROLE_NOT_FOUND));
        when(refs.roleExists(ROLE)).thenReturn(true);
        assertThatThrownBy(() -> changeUserRoles.assign(USER, ROLE, NOW, NOW.minusSeconds(1), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_ASSIGNMENT_PERIOD));
    }

    @Test
    void endAssignmentPublishesForTheAssignedUser() {
        UserRoleAssignmentEntity a = UserRoleAssignmentEntity.assign(USER, ROLE, NOW.minusSeconds(100), null, null);
        UUID id = UUID.randomUUID();
        when(assignments.findById(id)).thenReturn(Optional.of(a));
        changeUserRoles.end(id, null);
        assertThat(a.getEffectiveTo()).isEqualTo(NOW);
        verify(events).publishEvent(new UserRolesChanged(USER));

        UUID missing = UUID.randomUUID();
        when(assignments.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> changeUserRoles.end(missing, null)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.ASSIGNMENT_NOT_FOUND));
        assertThatThrownBy(() -> changeUserRoles.end(id, NOW.minusSeconds(500))).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.INVALID_ASSIGNMENT_PERIOD));
    }

    // ------------------------------------------------------ PermissionCacheInvalidator

    @Test
    void invalidatorEvictsRoleMembersAndSwallowsFailures() {
        PermissionQueryRepository q = mock(PermissionQueryRepository.class);
        PermissionCache c = mock(PermissionCache.class);
        PermissionCacheInvalidator invalidator = new PermissionCacheInvalidator(c, q);

        when(q.findUserIdsByRoleId(ROLE)).thenReturn(Set.of(USER));
        invalidator.on(new RolePermissionsChanged(ROLE));
        verify(c).evictAll(Set.of(USER));

        doThrow(new IllegalStateException("redis down")).when(c).evictAll(any());
        assertThatCode(() -> invalidator.on(new RolePermissionsChanged(ROLE))).doesNotThrowAnyException();
        doThrow(new IllegalStateException("redis down")).when(c).evict(any());
        assertThatCode(() -> invalidator.on(new UserRolesChanged(USER))).doesNotThrowAnyException();
    }
}
