package com.htv.shared.auth;

import java.util.UUID;

/**
 * Định nghĩa khóa cache quyền, dùng chung giữa identity (ghi) và các service (đọc).
 * Giá trị là SET Redis các mã quyền. User không có quyền nào lưu đúng một phần tử {@link #NONE}.
 */
public final class PermissionCacheKeys {

    public static final String PREFIX = "iam:perm:user:";
    /** Dấu "không có quyền nào", tránh hỏi identity lặp lại. Không phải mã quyền hợp lệ. */
    public static final String NONE = "__none__";

    private PermissionCacheKeys() {
    }

    public static String userKey(UUID userId) {
        return PREFIX + userId;
    }
}
