package com.htv.identity.domain.model.authorization;

import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Role được cấp quyền nào. Duy nhất theo {@code (role_id, permission_id)}. */
@Entity
@Table(name = "iam_role_permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermissionEntity extends AuditableEntity {

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    public static RolePermissionEntity grant(UUID roleId, UUID permissionId) {
        if (roleId == null || permissionId == null) {
            throw new IllegalArgumentException("roleId and permissionId are required");
        }
        RolePermissionEntity rp = new RolePermissionEntity();
        rp.roleId = roleId;
        rp.permissionId = permissionId;
        return rp;
    }
}
