package com.htv.shared.common;

/**
 * Mã lỗi nhận từ service khác (qua HTTP body hoặc trailer gRPC). Giữ nguyên {@code code} để mã lỗi đi xuyên service.
 */
public record RemoteErrorCode(String code, String message, int httpStatus, int grpcStatus) implements ErrorCode {
}
