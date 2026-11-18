package com.htv.shared.auth;

import java.util.List;

/**
 * Gửi danh mục quyền tới identity-service. Cài đặt mặc định gọi gRPC ({@code grpc-starter});
 * identity-service tự cài đặt bản ghi trực tiếp vào DB.
 */
public interface PermissionCatalogPublisher {

    PublishResult publish(PermissionCatalog catalog);

    /** Kết quả xử lý của identity. */
    record PublishResult(int created, int updated, int unchanged, List<RejectedCode> rejected) {

        public PublishResult {
            rejected = List.copyOf(rejected == null ? List.of() : rejected);
        }
    }

    /** Một mã bị từ chối, kèm lý do ({@code OWNED_BY_OTHER}, {@code INVALID_FORMAT}). */
    record RejectedCode(String code, String reason) {
    }
}
