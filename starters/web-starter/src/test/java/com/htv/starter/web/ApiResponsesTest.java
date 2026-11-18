package com.htv.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.api.model.BaseResponse;
import com.htv.shared.common.CommonErrorCode;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponsesTest {

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
    }

    @Test
    void successCarriesDataAndTraceId() {
        try (ContextHolder.Scope ignored = ContextHolder.open(RequestContext.builder().traceId("t-9").build())) {
            BaseResponse<String> r = ApiResponses.success("ok");
            assertThat(r.getCode()).isEqualTo("COMMON-0000");
            assertThat(r.getMessage()).isEqualTo("Success");
            assertThat(r.getData()).isEqualTo("ok");
            assertThat(r.getTraceId()).isEqualTo("t-9");
        }
    }

    @Test
    void errorHasNoDataAndNoTraceIdOutsideRequest() {
        BaseResponse<Void> r = ApiResponses.error(CommonErrorCode.FORBIDDEN);
        assertThat(r.getCode()).isEqualTo("COMMON-4030");
        assertThat(r.getData()).isNull();
        assertThat(r.getTraceId()).isNull();
        assertThat(ApiResponses.success().getCode()).isEqualTo("COMMON-0000");
        assertThat(ApiResponses.error(CommonErrorCode.INVALID_REQUEST, java.util.List.of("x")).getData())
                .isEqualTo(java.util.List.of("x"));
    }
}
