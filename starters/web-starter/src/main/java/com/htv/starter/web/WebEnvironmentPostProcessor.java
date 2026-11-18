package com.htv.starter.web;

import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Mặc định ở mức ưu tiên thấp nhất: profile {@code prod} tắt OpenAPI và Swagger UI
 * (không để lộ danh sách endpoint). Service bật lại bằng {@code springdoc.*} trong application.yml.
 */
public class WebEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "htvWebDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("htv.web.enabled", Boolean.class, true)) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, Map.of(
                    "springdoc.api-docs.enabled", "false",
                    "springdoc.swagger-ui.enabled", "false")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
