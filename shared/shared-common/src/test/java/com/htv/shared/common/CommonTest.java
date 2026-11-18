package com.htv.shared.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommonTest {

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
    }

    @Test
    void commonErrorCodesAreUniqueAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (CommonErrorCode c : CommonErrorCode.values()) {
            assertThat(c.code()).matches("COMMON-\\d{4}");
            assertThat(seen.add(c.code())).as(c.code()).isTrue();
            assertThat(c.message()).isNotBlank();
            assertThat(c.httpStatus()).isBetween(200, 599);
            assertThat(c.grpcStatus()).isBetween(0, 16);
        }
    }

    @Test
    void businessExceptionKeepsErrorCode() {
        BusinessException e = new BusinessException(CommonErrorCode.NOT_FOUND);
        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(e).hasMessage("Resource not found").hasNoCause();

        Throwable cause = new IllegalStateException("db");
        BusinessException e2 = new BusinessException(CommonErrorCode.INTERNAL_ERROR, "detail", cause);
        assertThat(e2).hasMessage("detail").hasCause(cause);
        assertThat(new BusinessException(CommonErrorCode.CONFLICT, "x")).hasMessage("x");
    }

    @Test
    void holderIsEmptyByDefault() {
        assertThat(ContextHolder.current()).isEmpty();
        assertThat(ContextHolder.userId()).isEmpty();
        assertThat(ContextHolder.traceId()).isEmpty();
    }

    @Test
    void scopeRestoresPreviousContext() {
        RequestContext outer = RequestContext.builder().traceId("t1").userId("u1").build();
        RequestContext inner = outer.toBuilder().userId("u2").build();
        ContextHolder.set(outer);
        try (ContextHolder.Scope ignored = ContextHolder.open(inner)) {
            assertThat(ContextHolder.userId()).contains("u2");
        }
        assertThat(ContextHolder.userId()).contains("u1");
        assertThat(ContextHolder.traceId()).contains("t1");
    }

    @Test
    void scopeOnEmptyHolderClearsAfterClose() {
        try (ContextHolder.Scope ignored = ContextHolder.open(RequestContext.builder().userId("u").build())) {
            assertThat(ContextHolder.current()).isPresent();
        }
        assertThat(ContextHolder.current()).isEmpty();
    }

    @Test
    void contextDoesNotLeakToOtherThreads() throws Exception {
        ContextHolder.set(RequestContext.builder().userId("u").build());
        AtomicReference<Boolean> present = new AtomicReference<>();
        Thread t = new Thread(() -> present.set(ContextHolder.current().isPresent()));
        t.start();
        t.join();
        assertThat(present.get()).isFalse();
    }

    @Test
    void settingNullClears() {
        ContextHolder.set(RequestContext.builder().userId("u").build());
        ContextHolder.set(null);
        assertThat(ContextHolder.current()).isEmpty();
    }
}
