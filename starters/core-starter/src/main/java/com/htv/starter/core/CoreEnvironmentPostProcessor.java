package com.htv.starter.core;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Mặc định ở mức ưu tiên THẤP NHẤT (application.yml của service luôn thắng):
 * profile {@code prod} ghi log JSON (structured logging của Spring Boot) ra console.
 * Ngoài prod giữ định dạng chữ để dễ đọc khi dev.
 */
public class CoreEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "htvCoreDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("htv.core.enabled", Boolean.class, true)) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(SOURCE_NAME, Map.of("logging.structured.format.console", "ecs")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
