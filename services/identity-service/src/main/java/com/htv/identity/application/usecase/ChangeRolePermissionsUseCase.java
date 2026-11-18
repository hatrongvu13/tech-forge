package com.htv.identity.application.usecase;

import com.htv.identity.domain.IdentityErrorCode;
import com.htv.identity.domain.event.RolePermissionsChanged;
import com.htv.identity.domain.model.authorization.RolePermissionEntity;
import com.htv.identity.domain.repository.IamReferenceRepository;
import com.htv.identity.domain.repository.RolePermissionRepository;
import com.htv.shared.common.BusinessException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp hoặc thu hồi quyền của role. Idempotent. Sau khi COMMIT, cache quyền của mọi người dùng mang role
 * này bị xóa (xem {@code PermissionCacheInvalidator}).
 */
@Service
public class ChangeRolePermissionsUseCase {

    private final RolePermissionRepository rolePermissions;
    private final IamReferenceRepository references;
    private final ApplicationEventPublisher events;

    public ChangeRolePermissionsUseCase(RolePermissionRepository rolePermissions, IamReferenceRepository references,
                                        ApplicationEventPublisher events) {
        this.rolePermissions = rolePermissions;
        this.references = references;
        this.events = events;
    }

    @Transactional
    public void grant(UUID roleId, UUID permissionId) {
        requireBoth(roleId, permissionId);
        if (rolePermissions.exists(roleId, permissionId)) {
            return;
        }
        rolePermissions.save(RolePermissionEntity.grant(roleId, permissionId));
        events.publishEvent(new RolePermissionsChanged(roleId));
    }

    @Transactional
    public void revoke(UUID roleId, UUID permissionId) {
        requireBoth(roleId, permissionId);
        if (rolePermissions.delete(roleId, permissionId) > 0) {
            events.publishEvent(new RolePermissionsChanged(roleId));
        }
    }

    private void requireBoth(UUID roleId, UUID permissionId) {
        if (!references.roleExists(roleId)) {
            throw new BusinessException(IdentityErrorCode.ROLE_NOT_FOUND);
        }
        if (!references.permissionExists(permissionId)) {
            throw new BusinessException(IdentityErrorCode.PERMISSION_NOT_FOUND);
        }
    }
}
