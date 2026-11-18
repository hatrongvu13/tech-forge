package com.htv.identity.application.port;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Cache quyền theo người dùng (Redis). Định nghĩa khóa/giá trị nằm ở {@code shared-auth}. */
public interface PermissionCache {

    void put(UUID userId, Set<String> permissions);

    void evict(UUID userId);

    void evictAll(Collection<UUID> userIds);
}
