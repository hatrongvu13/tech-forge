package com.htv.identity.domain.model.authorization;

import com.htv.starter.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vai trò. {@code systemRole = true} là role hệ thống (ví dụ SUPER_ADMIN), không được xóa. */
@Entity
@Table(name = "iam_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleEntity extends AuditableEntity {

    @Column(name = "code", nullable = false, updatable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @Column(name = "description", length = 500)
    private String description;

    public static RoleEntity create(String code, String name, String description) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("code and name are required");
        }
        RoleEntity r = new RoleEntity();
        r.code = code.trim();
        r.name = name.trim();
        r.description = description;
        r.systemRole = false;
        return r;
    }

    public void rename(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name.trim();
        this.description = description;
    }
}
