package com.htv.identity.domain;

import com.htv.shared.common.ErrorCode;

/** Mã lỗi riêng của identity-service. Tiền tố {@code IDENTITY}. */
public enum IdentityErrorCode implements ErrorCode {

    INVALID_ASSIGNMENT_PERIOD("IDENTITY-4001", "Invalid assignment period", 400, 3),
    USER_NOT_FOUND("IDENTITY-4041", "User not found", 404, 5),
    ROLE_NOT_FOUND("IDENTITY-4042", "Role not found", 404, 5),
    PERMISSION_NOT_FOUND("IDENTITY-4043", "Permission not found", 404, 5),
    ASSIGNMENT_NOT_FOUND("IDENTITY-4044", "Role assignment not found", 404, 5);

    private final String code;
    private final String message;
    private final int httpStatus;
    private final int grpcStatus;

    IdentityErrorCode(String code, String message, int httpStatus, int grpcStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.grpcStatus = grpcStatus;
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
