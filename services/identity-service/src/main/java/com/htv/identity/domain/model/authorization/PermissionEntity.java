package com.htv.identity.domain.model.authorization;

import com.htv.shared.auth.PermissionCodes;
import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Quyền trong danh mục. Mã là bất biến. Do service khai báo bằng {@code @RequirePermission}
 * và tự đồng bộ vào đây (M6). {@code active = false} khi mã không còn trong code.
 */
@Entity
@Table(name = "iam_permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionEntity extends AuditableEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 150)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "service_name", length = 100)
    private String serviceName;

    @Column(name = "active", nullable = false)
    private boolean active;

    public static PermissionEntity declare(String code, String description, String serviceName) {
        if (!PermissionCodes.isValidDeclared(code)) {
            throw new IllegalArgumentException("Invalid permission code: " + code);
        }
        PermissionEntity p = new PermissionEntity();
        p.code = code;
        p.description = description;
        p.serviceName = serviceName;
        p.active = true;
        return p;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate(String description) {
        this.active = true;
        this.description = description;
    }
}
