package com.htv.shared.common;

import java.util.Objects;

/**
 * Lỗi nghiệp vụ có chủ đích. Handler ở web/gRPC dịch thành HTTP status / gRPC status theo
 * {@link ErrorCode}. {@code detail} chỉ để ghi log, không trả cho client.
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null);
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail != null ? detail : Objects.requireNonNull(errorCode, "errorCode").message(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
