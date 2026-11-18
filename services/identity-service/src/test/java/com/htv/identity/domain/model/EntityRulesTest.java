package com.htv.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.identity.domain.enums.CredentialType;
import com.htv.identity.domain.enums.TokenType;
import com.htv.identity.domain.enums.UserStatus;
import com.htv.identity.domain.model.audit.AuditLogEntity;
import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.RoleEntity;
import com.htv.identity.domain.model.authorization.RolePermissionEntity;
import com.htv.identity.domain.model.authorization.ServiceCatalogEntity;
import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import com.htv.identity.domain.model.identity.UserCredentialEntity;
import com.htv.identity.domain.model.identity.UserEntity;
import com.htv.identity.domain.model.identity.UserSessionEntity;
import com.htv.identity.domain.model.identity.UserTokenEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Luật nghiệp vụ của entity, không cần DB. */
class EntityRulesTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void userRegisterNormalizesAndActivates() {
        UserEntity u = UserEntity.register("  alice ", " ", "", " Alice ");
        assertThat(u.getUsername()).isEqualTo("alice");
        assertThat(u.getEmail()).isNull();
        assertThat(u.getPhone()).isNull();
        assertThat(u.getFullName()).isEqualTo("Alice");
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);
        u.changeStatus(UserStatus.LOCKED);
        assertThat(u.getStatus()).isEqualTo(UserStatus.LOCKED);
        u.updateProfile("a@x.com", "0123", "A");
        assertThat(u.getEmail()).isEqualTo("a@x.com");
        assertThatThrownBy(() -> UserEntity.register(" ", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> u.changeStatus(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localCredentialNeedsHashAndChangePasswordBumpsVersion() {
        assertThatThrownBy(() -> UserCredentialEntity.local(ID, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserCredentialEntity.local(null, "h")).isInstanceOf(IllegalArgumentException.class);
        UserCredentialEntity c = UserCredentialEntity.local(ID, "h1");
        assertThat(c.getPasswordVersion()).isEqualTo(1);
        c.changePassword("h2", T0);
        assertThat(c.getPasswordVersion()).isEqualTo(2);
        assertThat(c.getPasswordHash()).isEqualTo("h2");
        assertThatThrownBy(() -> c.changePassword(" ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalCredentialHasNoPassword() {
        UserCredentialEntity c = UserCredentialEntity.external(ID, CredentialType.OIDC);
        assertThat(c.getPasswordHash()).isNull();
        assertThatThrownBy(() -> c.changePassword("h", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> UserCredentialEntity.external(ID, CredentialType.LOCAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserCredentialEntity.external(ID, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionLifecycle() {
        UserSessionEntity s = UserSessionEntity.open(ID, "web", "1.1.1.1", "ua", T0.plusSeconds(60));
        assertThat(s.isActive(T0)).isTrue();
        assertThat(s.isActive(T0.plusSeconds(61))).isFalse();
        s.revoke(T0.plusSeconds(1));
        s.revoke(T0.plusSeconds(2));
        assertThat(s.getRevokedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(s.isActive(T0)).isFalse();
        assertThatThrownBy(() -> UserSessionEntity.open(null, null, null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenStoresHashOnlyAndRevokesOnce() {
        UserTokenEntity t = UserTokenEntity.issue(ID, TokenType.REFRESH, "hash", T0);
        t.revoke(T0);
        t.revoke(T0.plusSeconds(5));
        assertThat(t.getRevokedAt()).isEqualTo(T0);
        assertThatThrownBy(() -> UserTokenEntity.issue(ID, TokenType.REFRESH, " ", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roleAndPermissionRules() {
        RoleEntity r = RoleEntity.create(" OPS ", " Ops ", "d");
        assertThat(r.getCode()).isEqualTo("OPS");
        assertThat(r.isSystemRole()).isFalse();
        r.rename("Ops2", null);
        assertThat(r.getName()).isEqualTo("Ops2");
        assertThatThrownBy(() -> r.rename(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoleEntity.create("", "n", null)).isInstanceOf(IllegalArgumentException.class);

        PermissionEntity p = PermissionEntity.declare("order:read", "d", "order-service");
        assertThat(p.isActive()).isTrue();
        p.deactivate();
        assertThat(p.isActive()).isFalse();
        p.activate("new");
        assertThat(p.isActive()).isTrue();
        assertThat(p.getDescription()).isEqualTo("new");
        assertThatThrownBy(() -> PermissionEntity.declare("*", null, null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(RolePermissionEntity.grant(ID, ID).getRoleId()).isEqualTo(ID);
        assertThatThrownBy(() -> RolePermissionEntity.grant(null, ID)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userRoleAssignmentPeriod() {
        UserRoleAssignmentEntity a = UserRoleAssignmentEntity.assign(ID, ID, T0, null, null);
        assertThat(a.isEffective(T0.minusSeconds(1))).isFalse();
        assertThat(a.isEffective(T0.plusSeconds(1))).isTrue();
        a.endAt(T0.plusSeconds(10));
        assertThat(a.isEffective(T0.plusSeconds(11))).isFalse();
        assertThatThrownBy(() -> a.endAt(T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserRoleAssignmentEntity.assign(ID, ID, T0, T0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserRoleAssignmentEntity.assign(ID, null, T0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalogAndAuditLogFactories() {
        ServiceCatalogEntity c = ServiceCatalogEntity.register("order-service", "1", "i", T0);
        c.update("2", "j", T0.plusSeconds(1));
        assertThat(c.getServiceVersion()).isEqualTo("2");
        assertThat(c.getRegisteredAt()).isEqualTo(T0.plusSeconds(1));
        assertThatThrownBy(() -> ServiceCatalogEntity.register(" ", "1", null, T0))
                .isInstanceOf(IllegalArgumentException.class);

        AuditLogEntity l = AuditLogEntity.of(null, "LOGIN", null, null, null);
        assertThat(l.getAction()).isEqualTo("LOGIN");
        assertThatThrownBy(() -> AuditLogEntity.of(null, " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
