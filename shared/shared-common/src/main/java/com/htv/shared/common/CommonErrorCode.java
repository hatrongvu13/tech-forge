package com.htv.shared.common;

/** Mã lỗi dùng chung cho mọi service. Tiền tố {@code COMMON}. */
public enum CommonErrorCode implements ErrorCode {

    SUCCESS("COMMON-0000", "Success", 200, 0),
    INVALID_REQUEST("COMMON-4000", "Invalid request", 400, 3),
    UNAUTHORIZED("COMMON-4010", "Unauthorized", 401, 16),
    FORBIDDEN("COMMON-4030", "Forbidden", 403, 7),
    NOT_FOUND("COMMON-4040", "Resource not found", 404, 5),
    METHOD_NOT_ALLOWED("COMMON-4050", "Method not allowed", 405, 12),
    CONFLICT("COMMON-4090", "Conflict", 409, 6),
    UNSUPPORTED_MEDIA_TYPE("COMMON-4150", "Unsupported media type", 415, 3),
    TOO_MANY_REQUESTS("COMMON-4290", "Too many requests", 429, 8),
    INTERNAL_ERROR("COMMON-5000", "Internal error", 500, 13),
    SERVICE_UNAVAILABLE("COMMON-5030", "Service unavailable", 503, 14),
    TIMEOUT("COMMON-5040", "Request timeout", 504, 4);

    private final String code;
    private final String message;
    private final int httpStatus;
    private final int grpcStatus;

    CommonErrorCode(String code, String message, int httpStatus, int grpcStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.grpcStatus = grpcStatus;
    }

    /** Mã chung ứng với một HTTP status (dùng khi phía bên kia không gửi mã lỗi riêng). */
    public static CommonErrorCode forHttpStatus(int status) {
        return switch (status) {
            case 200 -> SUCCESS;
            case 400 -> INVALID_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 409 -> CONFLICT;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            case 504 -> TIMEOUT;
            default -> status >= 500 ? INTERNAL_ERROR : INVALID_REQUEST;
        };
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public int grpcStatus() {
        return grpcStatus;
    }
}
