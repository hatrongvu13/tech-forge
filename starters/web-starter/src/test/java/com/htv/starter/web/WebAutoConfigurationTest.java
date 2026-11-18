package com.htv.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class WebAutoConfigurationTest {

    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void registersFiltersInOrderAndHandler() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(ctx).hasBean("htvRequestContextFilter").hasBean("htvAccessLogFilter");
            FilterRegistrationBean<?> rc = ctx.getBean("htvRequestContextFilter", FilterRegistrationBean.class);
            FilterRegistrationBean<?> al = ctx.getBean("htvAccessLogFilter", FilterRegistrationBean.class);
            assertThat(rc.getOrder()).isLessThan(al.getOrder());
            // chạy trước Spring Security (order -100) để log được 401/403
            assertThat(al.getOrder()).isLessThan(-100);
            assertThat(ctx).doesNotHaveBean(UserIdResolver.class);
            assertThat(ctx.getBean(WebProperties.class).getAccessLog().getExcludePaths()).containsExactly("/actuator/**");
        });
    }

    @Test
    void userHeaderIsTrustedOnlyWhenEnabled() {
        runner.withPropertyValues("htv.web.trust-user-header=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(UserIdResolver.class));
    }

    @Test
    void accessLogCanBeSwitchedOff() {
        runner.withPropertyValues("htv.web.access-log.enabled=false")
                .run(ctx -> assertThat(ctx).hasBean("htvRequestContextFilter").doesNotHaveBean("htvAccessLogFilter"));
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("htv.web.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GlobalExceptionHandler.class)
                        .doesNotHaveBean("htvRequestContextFilter"));
    }

    @Test
    void notActiveOutsideServletApplications() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void serviceCanOverrideHandlerAndResolver() {
        runner.withBean(GlobalExceptionHandler.class, () -> new GlobalExceptionHandler() { })
                .run(ctx -> assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class));
        runner.withPropertyValues("htv.web.trust-user-header=true")
                .withBean("headerUserIdResolver", UserIdResolver.class, () -> r -> java.util.Optional.of("x"))
                .run(ctx -> assertThat(ctx).hasSingleBean(UserIdResolver.class));
    }

    @Test
    void prodDisablesSwaggerButServiceCanReEnable() {
        StandardEnvironment prod = new StandardEnvironment();
        prod.setActiveProfiles("prod");
        new WebEnvironmentPostProcessor().postProcessEnvironment(prod, new SpringApplication());
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");

        StandardEnvironment reEnabled = new StandardEnvironment();
        reEnabled.setActiveProfiles("prod");
        reEnabled.getPropertySources().addFirst(
                new MapPropertySource("app", Map.of("springdoc.swagger-ui.enabled", "true")));
        new WebEnvironmentPostProcessor().postProcessEnvironment(reEnabled, new SpringApplication());
        assertThat(reEnabled.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");

        StandardEnvironment dev = new StandardEnvironment();
        new WebEnvironmentPostProcessor().postProcessEnvironment(dev, new SpringApplication());
        assertThat(dev.getProperty("springdoc.swagger-ui.enabled")).isNull();

        StandardEnvironment off = new StandardEnvironment();
        off.setActiveProfiles("prod");
        off.getPropertySources().addFirst(new MapPropertySource("app", Map.of("htv.web.enabled", "false")));
        new WebEnvironmentPostProcessor().postProcessEnvironment(off, new SpringApplication());
        assertThat(off.getProperty("springdoc.swagger-ui.enabled")).isNull();
        assertThat(new WebEnvironmentPostProcessor().getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
    }
}
