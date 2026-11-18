package com.htv.identity.application.usecase;

import com.htv.identity.application.SingleFlight;
import com.htv.identity.application.port.PermissionCache;
import com.htv.identity.domain.IdentityErrorCode;
import com.htv.identity.domain.enums.UserStatus;
import com.htv.identity.domain.repository.PermissionQueryRepository;
import com.htv.shared.common.BusinessException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tính quyền đang hiệu lực của người dùng và ghi vào cache (service khác sẽ đọc cache này).
 * Người dùng không {@code ACTIVE} không có quyền nào. Người dùng không tồn tại: {@code IDENTITY-4041}.
 *
 * <p>Ghi cache thất bại không làm hỏng kết quả (chỉ cảnh báo). Có một khoảng đua nhỏ: nếu quyền đổi và cache bị
 * xóa đúng lúc một lần tính cũ đang chạy, bản cũ có thể được ghi lại; TTL của khóa giới hạn độ trễ đó.
 */
@Service
public class GetUserPermissionsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetUserPermissionsUseCase.class);

    private final PermissionQueryRepository queries;
    private final PermissionCache cache;
    private final Clock clock;
    private final SingleFlight<UUID, Set<String>> singleFlight = new SingleFlight<>();

    public GetUserPermissionsUseCase(PermissionQueryRepository queries, PermissionCache cache, Clock clock) {
        this.queries = queries;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<String> execute(UUID userId) {
        return singleFlight.run(userId, () -> compute(userId));
    }

    private Set<String> compute(UUID userId) {
        UserStatus status = queries.findUserStatus(userId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.USER_NOT_FOUND));
        Set<String> permissions = status == UserStatus.ACTIVE
                ? Set.copyOf(queries.findEffectivePermissionCodes(userId, clock.instant()))
                : Set.of();
        try {
            cache.put(userId, permissions);
        } catch (RuntimeException e) {
            LOG.warn("Cannot write permission cache for user {}: {}", userId, e.getClass().getSimpleName());
        }
        return permissions;
    }
}
