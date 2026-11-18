package com.htv.shared.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Danh mục quyền một service khai báo, gửi cho identity-service lúc khởi động.
 *
 * @param serviceName    tên service (spring.application.name)
 * @param serviceVersion version build
 * @param instanceId     định danh instance, chỉ để truy vết
 * @param permissions    các quyền; mã phải hợp lệ và không trùng
 */
public record PermissionCatalog(String serviceName, String serviceVersion, String instanceId,
                                List<PermissionDef> permissions) {

    public PermissionCatalog {
        require(serviceName, "serviceName");
        require(serviceVersion, "serviceVersion");
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
        Set<String> seen = new HashSet<>();
        for (PermissionDef p : permissions) {
            if (!seen.add(p.code())) {
                throw new IllegalArgumentException("Duplicate permission code in " + serviceName + ": " + p.code());
            }
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /** Một quyền được khai báo. */
    public record PermissionDef(String code, String description) {

        public PermissionDef {
            if (!PermissionCodes.isValidDeclared(code)) {
                throw new IllegalArgumentException("Invalid permission code: " + code);
            }
            description = description == null ? "" : description;
        }
    }
}
