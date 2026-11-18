package com.htv.identity.domain.event;

import java.util.UUID;

/** Role của một người dùng đã đổi. Cache quyền của người dùng này phải bị xóa. */
public record UserRolesChanged(UUID userId) {
}
