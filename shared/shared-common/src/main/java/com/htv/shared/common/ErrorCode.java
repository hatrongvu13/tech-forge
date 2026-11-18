package com.htv.shared.common;

/**
 * Mã lỗi nghiệp vụ. Mỗi service khai báo enum riêng implement interface này
 * (ví dụ {@code IdentityErrorCode}), mã có dạng {@code <MIỀN>-<số>}: {@code IDENTITY-1001}.
 */
public interface ErrorCode {

    /** Mã ổn định, đi xuyên service (HTTP body, gRPC trailer). */
    String code();

    /** Thông điệp an toàn để trả cho client. Không chứa chi tiết hạ tầng. */
    String message();

    /** HTTP status tương ứng. */
    int httpStatus();

    /** Mã trạng thái gRPC chuẩn (0..16, theo {@code io.grpc.Status.Code}). Dùng số để không phụ thuộc gRPC. */
    int grpcStatus();
}
