package com.htv.identity.application;

import com.htv.identity.application.port.PermissionCache;
import com.htv.identity.domain.event.PermissionCatalogChanged;
import com.htv.identity.domain.event.RolePermissionsChanged;
import com.htv.identity.domain.event.UserRolesChanged;
import com.htv.identity.domain.repository.PermissionQueryRepository;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Xóa cache quyền SAU KHI giao dịch đổi quyền đã COMMIT (xóa trước commit thì người đọc có thể nạp lại dữ liệu cũ).
 * Lỗi khi xóa chỉ ghi cảnh báo: TTL của khóa là mức trễ tối đa. Không bao giờ làm hỏng thao tác đổi quyền.
 */
@Component
public class PermissionCacheInvalidator {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionCacheInvalidator.class);

    private final PermissionCache cache;
    private final PermissionQueryRepository queries;

    public PermissionCacheInvalidator(PermissionCache cache, PermissionQueryRepository queries) {
        this.cache = cache;
        this.queries = queries;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(RolePermissionsChanged event) {
        try {
            Set<UUID> users = queries.findUserIdsByRoleId(event.roleId());
            cache.evictAll(users);
            LOG.info("Evicted permission cache of {} user(s) for role {}", users.size(), event.roleId());
        } catch (RuntimeException e) {
            LOG.warn("Cannot evict permission cache for role {}: {} (entries expire by TTL)", event.roleId(),
                    e.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRolesChanged event) {
        try {
            cache.evict(event.userId());
        } catch (RuntimeException e) {
            LOG.warn("Cannot evict permission cache for user {}: {} (entry expires by TTL)", event.userId(),
                    e.getClass().getSimpleName());
        }
    }

    /** Quyền được bật/tắt khi một service đăng ký danh mục: người dùng mang các quyền đó phải tính lại. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(PermissionCatalogChanged event) {
        try {
            Set<UUID> users = queries.findUserIdsByPermissionIds(event.permissionIds());
            cache.evictAll(users);
            LOG.info("Evicted permission cache of {} user(s) after {} permission(s) changed state", users.size(),
                    event.permissionIds().size());
        } catch (RuntimeException e) {
            LOG.warn("Cannot evict permission cache after catalog change: {} (entries expire by TTL)",
                    e.getClass().getSimpleName());
        }
    }
}
