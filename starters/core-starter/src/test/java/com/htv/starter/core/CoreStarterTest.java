package com.htv.starter.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.task.TaskDecorator;

class CoreStarterTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));

    private static RequestContext ctx(String user) {
        return RequestContext.builder().traceId("t-1").spanId("s-1").userId(user).channel("web").build();
    }

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
        MDC.clear();
    }

    // ---- MdcContextBinder

    @Test
    void binderSetsHolderAndMdcThenRestores() {
        MDC.put("keep", "me");
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(ctx("u1"))) {
            assertThat(ContextHolder.userId()).contains("u1");
            assertThat(MDC.get(MdcContextBinder.TRACE_ID)).isEqualTo("t-1");
            assertThat(MDC.get(MdcContextBinder.SPAN_ID)).isEqualTo("s-1");
            assertThat(MDC.get(MdcContextBinder.USER_ID)).isEqualTo("u1");
            assertThat(MDC.get(MdcContextBinder.CHANNEL)).isEqualTo("web");
        }
        assertThat(ContextHolder.current()).isEmpty();
        assertThat(MDC.get(MdcContextBinder.USER_ID)).isNull();
        assertThat(MDC.get("keep")).isEqualTo("me");
    }

    @Test
    void binderClearsMdcWhenNothingBeforeAndSkipsBlank() {
        try (MdcContextBinder.Scope ignored =
                     MdcContextBinder.open(RequestContext.builder().traceId("t").userId(" ").build())) {
            assertThat(MDC.get(MdcContextBinder.USER_ID)).isNull();
            assertThat(MDC.get(MdcContextBinder.TRACE_ID)).isEqualTo("t");
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void binderUpdateChangesUserWithinScope() {
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(ctx(null))) {
            assertThat(MDC.get(MdcContextBinder.USER_ID)).isNull();
            MdcContextBinder.update(ctx("u2"));
            assertThat(ContextHolder.userId()).contains("u2");
            assertThat(MDC.get(MdcContextBinder.USER_ID)).isEqualTo("u2");
            MdcContextBinder.update(null);
            assertThat(MDC.get(MdcContextBinder.TRACE_ID)).isNull();
        }
    }

    // ---- TaskDecorator

    @Test
    void taskDecoratorCopiesContextToOtherThreadAndCleansUp() throws Exception {
        AtomicReference<String> userInTask = new AtomicReference<>();
        AtomicReference<String> mdcInTask = new AtomicReference<>();
        AtomicReference<Boolean> leakedAfter = new AtomicReference<>();

        Runnable decorated;
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(ctx("u3"))) {
            decorated = new ContextPropagatingTaskDecorator().decorate(() -> {
                userInTask.set(ContextHolder.userId().orElse(null));
                mdcInTask.set(MDC.get(MdcContextBinder.TRACE_ID));
            });
        }
        Thread t = new Thread(() -> {
            decorated.run();
            leakedAfter.set(ContextHolder.current().isPresent() || MDC.get(MdcContextBinder.TRACE_ID) != null);
        });
        t.start();
        t.join();

        assertThat(userInTask.get()).isEqualTo("u3");
        assertThat(mdcInTask.get()).isEqualTo("t-1");
        assertThat(leakedAfter.get()).isFalse();
    }

    @Test
    void taskDecoratorWithoutContextRunsClean() {
        AtomicReference<Boolean> present = new AtomicReference<>();
        new ContextPropagatingTaskDecorator().decorate(() -> present.set(ContextHolder.current().isPresent())).run();
        assertThat(present.get()).isFalse();
    }

    // ---- Auto-configuration

    @Test
    void registersTaskDecoratorAndProperties() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TaskDecorator.class);
            assertThat(ctx.getBean(CoreProperties.class).isEnabled()).isTrue();
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("htv.core.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(TaskDecorator.class));
    }

    @Test
    void serviceCanOverrideDecorator() {
        runner.withBean("mine", TaskDecorator.class, () -> r -> r).run(ctx -> {
            assertThat(ctx).hasSingleBean(TaskDecorator.class);
            assertThat(ctx.getBean(TaskDecorator.class)).isNotInstanceOf(ContextPropagatingTaskDecorator.class);
        });
    }

    // ---- EnvironmentPostProcessor

    @Test
    void prodProfileGetsJsonLoggingButServiceCanOverride() {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");
        new CoreEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("ecs");

        StandardEnvironment env2 = new StandardEnvironment();
        env2.setActiveProfiles("prod");
        env2.getPropertySources().addFirst(
                new MapPropertySource("app", java.util.Map.of("logging.structured.format.console", "logstash")));
        new CoreEnvironmentPostProcessor().postProcessEnvironment(env2, new SpringApplication());
        assertThat(env2.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    }

    @Test
    void devProfileKeepsPlainLoggingAndDisabledStarterSkips() {
        StandardEnvironment dev = new StandardEnvironment();
        dev.setActiveProfiles("dev");
        new CoreEnvironmentPostProcessor().postProcessEnvironment(dev, new SpringApplication());
        assertThat(dev.getProperty("logging.structured.format.console")).isNull();

        StandardEnvironment off = new StandardEnvironment();
        off.setActiveProfiles("prod");
        off.getPropertySources().addFirst(new MapPropertySource("app", java.util.Map.of("htv.core.enabled", "false")));
        new CoreEnvironmentPostProcessor().postProcessEnvironment(off, new SpringApplication());
        assertThat(off.getProperty("logging.structured.format.console")).isNull();
        assertThat(new CoreEnvironmentPostProcessor().getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
}
