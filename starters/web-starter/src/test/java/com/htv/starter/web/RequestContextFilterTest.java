package com.htv.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.htv.shared.common.ContextHolder;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequestContextFilterTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
        MDC.clear();
    }

    private MockMvc mvc(List<UserIdResolver> resolvers) {
        return MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .addFilters(new RequestContextFilter(resolvers)).build();
    }

    @Test
    void usesTraceparentAndEchoesTraceIdHeader() throws Exception {
        mvc(List.of()).perform(get("/t/ctx").header("traceparent", TRACEPARENT))
                .andExpect(header().string("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736"))
                .andExpect(jsonPath("$.traceId").value("4bf92f3577b34da6a3ce929d0e0e4736"));
    }

    @Test
    void fallsBackToXTraceIdThenGenerates() throws Exception {
        mvc(List.of()).perform(get("/t/ctx").header("X-Trace-Id", "abc-123"))
                .andExpect(jsonPath("$.traceId").value("abc-123"));
        String generated = mvc(List.of()).perform(get("/t/ctx")).andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(generated).matches("[0-9a-f]{32}");
    }

    @Test
    void mdcTraceIdFromTracingWins() throws Exception {
        MDC.put("traceId", "from-micrometer");
        mvc(List.of()).perform(get("/t/ctx").header("traceparent", TRACEPARENT))
                .andExpect(jsonPath("$.traceId").value("from-micrometer"));
    }

    @Test
    void unsafeClientValuesAreRejected() throws Exception {
        String evil = "a b\\nforged log line";
        String traceId = mvc(List.of()).perform(get("/t/ctx").header("X-Trace-Id", evil).header("X-Channel", evil))
                .andExpect(jsonPath("$.channel").value("null"))
                .andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        String tooLong = "a".repeat(65);
        assertThat(mvc(List.of()).perform(get("/t/ctx").header("X-Trace-Id", tooLong)).andReturn()
                .getResponse().getHeader("X-Trace-Id")).isNotEqualTo(tooLong);
    }

    @Test
    void userIdIgnoredWithoutResolver() throws Exception {
        mvc(List.of()).perform(get("/t/ctx").header("X-User-Id", "attacker"))
                .andExpect(jsonPath("$.userId").value("-"));
    }

    @Test
    void userIdFromTrustedHeaderResolver() throws Exception {
        mvc(List.of(new HeaderUserIdResolver()))
                .perform(get("/t/ctx").header("X-User-Id", "u-7").header("X-Channel", "mobile"))
                .andExpect(jsonPath("$.userId").value("u-7"))
                .andExpect(jsonPath("$.channel").value("mobile"));
    }

    @Test
    void firstResolverWithValueWins() throws Exception {
        UserIdResolver none = r -> java.util.Optional.empty();
        UserIdResolver fixed = r -> java.util.Optional.of("from-jwt");
        mvc(List.of(none, fixed, new HeaderUserIdResolver()))
                .perform(get("/t/ctx").header("X-User-Id", "u-7"))
                .andExpect(jsonPath("$.userId").value("from-jwt"));
    }

    @Test
    void contextAndMdcAreCleanedAfterEachRequest() throws Exception {
        MockMvc mvc = mvc(List.of(new HeaderUserIdResolver()));
        mvc.perform(get("/t/ctx").header("X-User-Id", "u-1"));
        assertThat(ContextHolder.current()).isEmpty();
        assertThat(MDC.get("userId")).isNull();
        mvc.perform(get("/t/ctx")).andExpect(jsonPath("$.userId").value("-"));
    }

    @Test
    void contextCleanedEvenWhenHandlerFails() throws Exception {
        try {
            mvc(List.of()).perform(get("/t/boom"));
        } catch (Exception expected) {
            // standalone không có advice: lỗi lan ra, ta chỉ quan tâm ngữ cảnh đã được dọn
        }
        assertThat(ContextHolder.current()).isEmpty();
    }
}
