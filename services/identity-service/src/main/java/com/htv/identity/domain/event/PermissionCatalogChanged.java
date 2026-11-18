package com.htv.identity.domain.event;

import java.util.Set;
import java.util.UUID;

/** Một số quyền vừa bị tắt (không còn trong code) hoặc được mở lại khi service đăng ký danh mục. */
public record PermissionCatalogChanged(Set<UUID> permissionIds) {

    public PermissionCatalogChanged {
        permissionIds = Set.copyOf(permissionIds);
    }
}
