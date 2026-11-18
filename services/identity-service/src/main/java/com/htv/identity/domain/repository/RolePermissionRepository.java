package com.htv.identity.domain.repository;

import com.htv.identity.domain.model.authorization.RolePermissionEntity;
import java.util.UUID;

public interface RolePermissionRepository {

    boolean exists(UUID roleId, UUID permissionId);

    RolePermissionEntity save(RolePermissionEntity rolePermission);

    /** @return số dòng đã xóa (0 hoặc 1) */
    int delete(UUID roleId, UUID permissionId);
}
