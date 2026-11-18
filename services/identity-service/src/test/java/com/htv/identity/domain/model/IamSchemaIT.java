package com.htv.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.identity.domain.enums.CredentialType;
import com.htv.identity.domain.model.audit.AuditLogEntity;
import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.RoleEntity;
import com.htv.identity.domain.model.authorization.RolePermissionEntity;
import com.htv.identity.domain.model.authorization.ServiceCatalogEntity;
import com.htv.identity.domain.model.authorization.UserRoleAssignmentEntity;
import com.htv.identity.domain.model.identity.UserCredentialEntity;
import com.htv.identity.domain.model.identity.UserEntity;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import com.htv.starter.jpa.JpaAutoConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Kiểm lược đồ Flyway (V1, V2) khớp entity (Hibernate {@code ddl-auto=validate}), seed, ràng buộc SQL
 * và JPA auditing, trên PostgreSQL thật.
 */
@DataJpaTest(properties = "spring.datasource.password=unused")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAutoConfiguration.class)
@Testcontainers
class IamSchemaIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    TestEntityManager tem;

    @Autowired
    EntityManager em;

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
    }

    private UserEntity newUser(String username) {
        return tem.persistAndFlush(UserEntity.register(username, username + "@example.com", null, "Name"));
    }

    @Test
    void seedCreatesSuperAdminWithAllPermission() {
        List<?> rows = em.createNativeQuery("""
                SELECT r.code, p.code FROM iam_role r
                JOIN iam_role_permission rp ON rp.role_id = r.id
                JOIN iam_permission p ON p.id = rp.permission_id
                WHERE r.code = 'SUPER_ADMIN'""").getResultList();
        assertThat(rows).hasSize(1);
        Object[] row = (Object[]) rows.get(0);
        assertThat(row[0]).isEqualTo("SUPER_ADMIN");
        assertThat(row[1]).isEqualTo("*");

        Boolean system = (Boolean) em.createNativeQuery("SELECT system_role FROM iam_role WHERE code='SUPER_ADMIN'")
                .getSingleResult();
        assertThat(system).isTrue();
    }

    @Test
    void auditingFillsCreatedAndUpdatedFromContext() {
        try (ContextHolder.Scope ignored = ContextHolder.open(RequestContext.builder().userId("tester").build())) {
            UserEntity u = newUser("alice");
            assertThat(u.getCreatedAt()).isNotNull();
            assertThat(u.getCreatedBy()).isEqualTo("tester");
            assertThat(u.getVersion()).isZero();

            u.updateProfile("alice2@example.com", null, "Alice");
            tem.flush();
            assertThat(u.getVersion()).isEqualTo(1);
            assertThat(u.getUpdatedBy()).isEqualTo("tester");
            assertThat(u.getUpdatedAt()).isAfterOrEqualTo(u.getCreatedAt());
        }
    }

    @Test
    void createdByIsNullWithoutContext() {
        assertThat(newUser("bob").getCreatedBy()).isNull();
    }

    @Test
    void usernameAndEmailAreUniqueIgnoringCase() {
        newUser("Carol");
        assertThatThrownBy(() -> tem.persistAndFlush(UserEntity.register("carol", "other@example.com", null, null)))
                .hasMessageContaining("ux_iam_user_username");
    }

    @Test
    void emailUniqueIgnoringCaseButNullAllowedManyTimes() {
        tem.persistAndFlush(UserEntity.register("d1", null, null, null));
        tem.persistAndFlush(UserEntity.register("d2", null, null, null));
        tem.persistAndFlush(UserEntity.register("d3", "Dave@Example.com", null, null));
        assertThatThrownBy(() -> tem.persistAndFlush(UserEntity.register("d4", "dave@example.com", null, null)))
                .hasMessageContaining("ux_iam_user_email");
    }

    @Test
    void oneCredentialPerTypePerUser() {
        UserEntity u = newUser("erin");
        tem.persistAndFlush(UserCredentialEntity.local(u.getId(), "hash"));
        tem.persistAndFlush(UserCredentialEntity.external(u.getId(), CredentialType.OIDC));
        assertThatThrownBy(() -> tem.persistAndFlush(UserCredentialEntity.local(u.getId(), "hash2")))
                .hasMessageContaining("ux_iam_credential_user_type");
    }

    @Test
    void credentialRequiresExistingUser() {
        assertThatThrownBy(() -> tem.persistAndFlush(UserCredentialEntity.local(UUID.randomUUID(), "h")))
                .hasMessageContaining("iam_user_credential");
    }

    @Test
    void roleCodeIsUniqueAndRolePermissionPairIsUnique() {
        RoleEntity role = tem.persistAndFlush(RoleEntity.create("OPS", "Ops", null));
        assertThatThrownBy(() -> tem.persistAndFlush(RoleEntity.create("OPS", "Ops 2", null)))
                .hasMessageContaining("ux_iam_role_code");
    }

    @Test
    void rolePermissionPairIsUnique() {
        RoleEntity role = tem.persistAndFlush(RoleEntity.create("OPS2", "Ops", null));
        PermissionEntity perm = tem.persistAndFlush(PermissionEntity.declare("order:read", "Read orders", "order-service"));
        tem.persistAndFlush(RolePermissionEntity.grant(role.getId(), perm.getId()));
        assertThatThrownBy(() -> tem.persistAndFlush(RolePermissionEntity.grant(role.getId(), perm.getId())))
                .hasMessageContaining("ux_iam_role_permission");
    }

    @Test
    void permissionCodeFormatIsEnforcedBySql() {
        assertThatThrownBy(() -> {
            em.createNativeQuery("""
                    INSERT INTO iam_permission (id, version, created_at, updated_at, code, active)
                    VALUES (gen_random_uuid(), 0, now(), now(), 'Bad Code', true)""").executeUpdate();
            em.flush();
        }).hasMessageContaining("ck_iam_permission_code");
    }

    @Test
    void permissionDeactivateKeepsRow() {
        PermissionEntity p = tem.persistAndFlush(PermissionEntity.declare("billing:read", "x", "billing"));
        p.deactivate();
        tem.flush();
        tem.clear();
        assertThat(tem.find(PermissionEntity.class, p.getId()).isActive()).isFalse();
    }

    @Test
    void userRolePeriodIsValidated() {
        UserEntity u = newUser("frank");
        RoleEntity r = tem.persistAndFlush(RoleEntity.create("R3", "R3", null));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UserRoleAssignmentEntity a = tem.persistAndFlush(
                UserRoleAssignmentEntity.assign(u.getId(), r.getId(), now, now.plus(1, ChronoUnit.DAYS), u.getId()));
        assertThat(a.isEffective(now.plusSeconds(1))).isTrue();
        assertThat(a.isEffective(now.plus(2, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void serviceCatalogNameIsUnique() {
        tem.persistAndFlush(ServiceCatalogEntity.register("order-service", "1.0", "i1", Instant.now()));
        assertThatThrownBy(() -> tem.persistAndFlush(
                ServiceCatalogEntity.register("order-service", "1.1", "i2", Instant.now())))
                .hasMessageContaining("ux_iam_service_catalog_name");
    }

    @Test
    void auditLogIsAppendOnlyAndSurvivesWithoutUser() {
        AuditLogEntity log = tem.persistAndFlush(
                AuditLogEntity.of(UUID.randomUUID(), "PERMISSION_REGISTERED", "permission", "order:read", "10.0.0.1"));
        assertThat(log.getCreatedAt()).isNotNull();
        tem.clear();
        assertThat(tem.find(AuditLogEntity.class, log.getId()).getAction()).isEqualTo("PERMISSION_REGISTERED");
    }
}
