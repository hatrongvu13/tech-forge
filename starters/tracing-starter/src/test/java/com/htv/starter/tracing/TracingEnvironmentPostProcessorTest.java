package com.htv.starter.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class TracingEnvironmentPostProcessorTest {

    private static StandardEnvironment run(Map<String, Object> app, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(profiles);
        if (!app.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("app", app));
        }
        new TracingEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        return env;
    }

    @Test
    void defaultsSeparateManagementPortProbesAndNoDetails() {
        StandardEnvironment env = run(Map.of("spring.application.name", "order-service"));
        assertThat(env.getProperty("management.server.port")).isEqualTo("8081");
        assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info,prometheus");
        assertThat(env.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
        assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(env.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
        assertThat(env.getProperty("management.observations.key-values.application")).isEqualTo("order-service");
        assertThat(env.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(env.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
    }

    @Test
    void prodSamplesTenPercent() {
        assertThat(run(Map.of(), "prod").getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
    }

    @Test
    void serviceSettingsWinAndPortIsConfigurable() {
        StandardEnvironment env = run(Map.of(
                "management.endpoints.web.exposure.include", "health",
                "management.tracing.sampling.probability", "0.5",
                "htv.tracing.management-port", "9001"));
        assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("0.5");
        assertThat(env.getProperty("management.server.port")).isEqualTo("9001");

        assertThat(run(Map.of("management.server.port", "7000")).getProperty("management.server.port"))
                .isEqualTo("7000");
    }

    @Test
    void canBeDisabled() {
        StandardEnvironment env = run(Map.of("htv.tracing.enabled", "false"));
        assertThat(env.getProperty("management.server.port")).isNull();
        assertThat(env.getPropertySources().contains(TracingEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
        assertThat(new TracingEnvironmentPostProcessor().getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
