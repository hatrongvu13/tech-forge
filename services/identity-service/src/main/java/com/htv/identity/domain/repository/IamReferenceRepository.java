package com.htv.identity.domain.repository;

import java.util.UUID;

/** Kiểm tra sự tồn tại của các aggregate gốc khi tham chiếu bằng id. */
public interface IamReferenceRepository {

    boolean userExists(UUID userId);

    boolean roleExists(UUID roleId);

    boolean permissionExists(UUID permissionId);
}
