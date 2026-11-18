package com.htv.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccessLogFilterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.LOGGER_NAME);
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private MockMvc mvc(List<String> exclude) {
        return MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestContextFilter(List.of(new HeaderUserIdResolver())),
                        new AccessLogFilter(exclude))
                .build();
    }

    @Test
    void logsOneLineWithoutQueryStringOrSecrets() throws Exception {
        mvc(List.of()).perform(get("/t/ctx").param("token", "s3cret").header("Authorization", "Bearer abc")
                .header("X-User-Id", "u-9").header("X-Trace-Id", "trace-1"));

        assertThat(appender.list).hasSize(1);
        String line = appender.list.get(0).getFormattedMessage();
        assertThat(line).contains("GET /t/ctx -> 200").contains("userId=u-9").contains("traceId=trace-1");
        assertThat(line).doesNotContain("s3cret").doesNotContain("Bearer").doesNotContain("token");
    }

    @Test
    void recordsErrorStatus() throws Exception {
        mvc(List.of()).perform(get("/t/business"));
        assertThat(appender.list.get(0).getFormattedMessage()).contains("-> 404");
    }

    @Test
    void excludedPathsAreNotLogged() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .addFilters(new AccessLogFilter(List.of("/t/**"))).build();
        mvc.perform(get("/t/ctx"));
        assertThat(appender.list).isEmpty();
    }

    @Test
    void logsStatus500AndRethrowsWhenExceptionEscapes() {
        MockMvc noAdvice = MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .addFilters(new AccessLogFilter(List.of())).build();
        assertThatThrownBy(() -> noAdvice.perform(get("/t/boom"))).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("-> 500").contains("userId=- ");
    }

    @Test
    void pathIsMaskedBeforeLogging() throws Exception {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.c2lnbmF0dXJlLXZhbHVl";
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WebFixtures.TestController())
                .addFilters(new AccessLogFilter(List.of())).build();
        mvc.perform(get("/t/magic/" + jwt));
        assertThat(appender.list.get(0).getFormattedMessage()).contains("/t/magic/***").doesNotContain(jwt);
    }
}
