package com.htv.starter.cached;

import com.htv.shared.auth.PermissionCacheKeys;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Đọc quyền từ Redis. Có khóa thì trả ngay. Không có khóa (miss) thì hỏi {@code delegate}
 * (thường là gRPC tới identity; identity tự ghi cache) rồi trả kết quả.
 *
 * <p>Fail-closed: Redis lỗi, hoặc miss mà không có {@code delegate}, thì ném
 * {@code COMMON-5030}. Không bao giờ trả tập rỗng giả.
 */
public class RedisPermissionProvider implements PermissionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RedisPermissionProvider.class);

    private final StringRedisTemplate redis;
    private final PermissionProvider delegate;

    public RedisPermissionProvider(StringRedisTemplate redis, PermissionProvider delegate) {
        this.redis = redis;
        this.delegate = delegate;
    }

    @Override
    public Set<String> permissionsOf(UUID userId) {
        Set<String> cached;
        try {
            cached = redis.opsForSet().members(PermissionCacheKeys.userKey(userId));
        } catch (RuntimeException e) {
            LOG.warn("Redis permission lookup failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "Redis permission lookup failed", e);
        }
        if (cached != null && !cached.isEmpty()) {
            Set<String> permissions = new HashSet<>(cached);
            permissions.remove(PermissionCacheKeys.NONE);
            return Set.copyOf(permissions);
        }
        if (delegate == null) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "Permissions not in cache and no identity lookup configured");
        }
        return delegate.permissionsOf(userId);
    }
}
