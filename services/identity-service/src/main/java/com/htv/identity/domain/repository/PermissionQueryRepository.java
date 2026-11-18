package com.htv.identity.domain.repository;

import com.htv.identity.domain.enums.UserStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Truy vấn đọc cho việc tính quyền. */
public interface PermissionQueryRepository {

    /** Trạng thái người dùng; rỗng nếu không tồn tại. */
    Optional<UserStatus> findUserStatus(UUID userId);

    /**
     * Mã các quyền đang hiệu lực của người dùng: gán role còn hiệu lực tại {@code now}, quyền còn {@code active}.
     * Có thể gồm {@code *} và {@code <miền>:*}.
     */
    Set<String> findEffectivePermissionCodes(UUID userId, Instant now);

    /** Mọi người dùng từng được gán role này (kể cả đã hết hạn), để xóa cache khi quyền của role đổi. */
    Set<UUID> findUserIdsByRoleId(UUID roleId);

    /** Mọi người dùng từng mang một role đang giữ một trong các quyền này, để xóa cache khi quyền bật/tắt. */
    Set<UUID> findUserIdsByPermissionIds(Collection<UUID> permissionIds);
}
