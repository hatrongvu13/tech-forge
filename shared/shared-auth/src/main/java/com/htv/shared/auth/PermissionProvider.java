package com.htv.shared.auth;

import java.util.Set;
import java.util.UUID;

/**
 * Nguồn quyền của người dùng. Cài đặt: Redis ({@code cached-starter}), gRPC tới identity
 * ({@code grpc-starter}). Muốn đổi cách tra (ví dụ thêm cache L1) chỉ cần thay hoặc bọc bean này.
 *
 * <p>Hợp đồng: trả về tập mã quyền đã cấp (có thể gồm {@code *} và {@code <miền>:*}); tập rỗng
 * nghĩa là người dùng không có quyền nào. Khi **không xác định được** (Redis/identity lỗi) phải ném
 * {@code BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE)} để bên gọi từ chối (fail-closed),
 * tuyệt đối không trả tập rỗng giả.
 */
public interface PermissionProvider {

    Set<String> permissionsOf(UUID userId);
}
