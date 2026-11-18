package com.htv.starter.web;

import com.htv.api.model.BaseResponse;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.ErrorCode;

/**
 * Dựng {@link BaseResponse} chuẩn. {@code traceId} lấy từ ngữ cảnh hiện tại.
 */
public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> BaseResponse<T> success(T data) {
        return build(CommonErrorCode.SUCCESS, data);
    }

    public static BaseResponse<Void> success() {
        return build(CommonErrorCode.SUCCESS, null);
    }

    public static BaseResponse<Void> error(ErrorCode code) {
        return build(code, null);
    }

    public static <T> BaseResponse<T> error(ErrorCode code, T data) {
        return build(code, data);
    }

    private static <T> BaseResponse<T> build(ErrorCode code, T data) {
        return BaseResponse.<T>builder()
                .traceId(ContextHolder.traceId().orElse(null))
                .code(code.code())
                .message(code.message())
                .data(data)
                .build();
    }
}
