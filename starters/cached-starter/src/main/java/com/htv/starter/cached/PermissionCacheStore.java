package com.htv.starter.cached;

import com.htv.shared.auth.PermissionCacheKeys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Ghi/xóa cache quyền theo định nghĩa chung ở {@link PermissionCacheKeys}. Identity dùng lớp này để ghi
 * và xóa; service dùng gián tiếp qua {@link RedisPermissionProvider}.
 *
 * <p>Ghi bằng một script Lua (xóa, thêm, đặt TTL) nên người đọc không bao giờ thấy trạng thái nửa chừng.
 */
public class PermissionCacheStore {

    private static final int EVICT_BATCH = 500;

    /** KEYS[1] = khóa; ARGV[1] = TTL giây; ARGV[2..] = các mã quyền. */
    private static final DefaultRedisScript<Long> PUT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('SADD', KEYS[1], unpack(ARGV, 2))
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public PermissionCacheStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    /** Ghi tập quyền của người dùng. Tập rỗng lưu dấu {@link PermissionCacheKeys#NONE}. */
    public void put(UUID userId, Set<String> permissions) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(Math.max(1, ttl.toSeconds())));
        if (permissions.isEmpty()) {
            args.add(PermissionCacheKeys.NONE);
        } else {
            args.addAll(permissions);
        }
        redis.execute(PUT_SCRIPT, List.of(PermissionCacheKeys.userKey(userId)), args.toArray());
    }

    public void evict(UUID userId) {
        redis.delete(PermissionCacheKeys.userKey(userId));
    }

    /** Xóa theo lô (nhiều người dùng thuộc một role). */
    public void evictAll(Collection<UUID> userIds) {
        List<String> keys = userIds.stream().map(PermissionCacheKeys::userKey).toList();
        for (int i = 0; i < keys.size(); i += EVICT_BATCH) {
            redis.delete(keys.subList(i, Math.min(keys.size(), i + EVICT_BATCH)));
        }
    }
}
