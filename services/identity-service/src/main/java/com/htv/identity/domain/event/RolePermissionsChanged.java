package com.htv.identity.domain.event;

import java.util.UUID;

/** Quyền của một role đã đổi. Mọi người dùng mang role này phải bị xóa cache quyền. */
public record RolePermissionsChanged(UUID roleId) {
}
