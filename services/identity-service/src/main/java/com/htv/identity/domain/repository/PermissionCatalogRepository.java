package com.htv.identity.domain.repository;

import com.htv.identity.domain.model.authorization.PermissionEntity;
import com.htv.identity.domain.model.authorization.ServiceCatalogEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PermissionCatalogRepository {

    /**
     * Tạo dòng của service nếu chưa có rồi KHÓA dòng đó đến hết giao dịch. Nhiều instance đăng ký cùng lúc sẽ
     * xếp hàng ở đây nên kết quả giống nhau.
     */
    ServiceCatalogEntity lockOrCreate(String serviceName, Instant now);

    Optional<PermissionEntity> findByCode(String code);

    /** Các quyền đang {@code active} của một service. */
    List<PermissionEntity> findActiveByService(String serviceName);

    PermissionEntity save(PermissionEntity permission);
}
