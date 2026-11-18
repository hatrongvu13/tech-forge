package com.htv.starter.feign;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Mặc định ở mức ưu tiên thấp nhất cho MỌI Feign client: timeout kết nối/đọc lấy từ {@code htv.feign.*}
 * (mặc định 2s/5s). Spring Cloud OpenFeign mặc định chờ 10s/60s, quá lâu khi một service phía sau treo.
 * Cấu hình {@code spring.cloud.openfeign.client.config.*} của service luôn thắng.
 */
public class FeignEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "htvFeignDefaults";
    private static final String PREFIX = "spring.cloud.openfeign.client.config.default.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("htv.feign.enabled", Boolean.class, true)) {
            return;
        }
        Duration connect = duration(environment, "htv.feign.connect-timeout", Duration.ofSeconds(2));
        Duration read = duration(environment, "htv.feign.read-timeout", Duration.ofSeconds(5));
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, Map.of(
                PREFIX + "connect-timeout", String.valueOf(connect.toMillis()),
                PREFIX + "read-timeout", String.valueOf(read.toMillis()))));
    }

    /** Đọc Duration theo cú pháp của Spring Boot ({@code 700ms}, {@code 5s}, {@code PT5S}) mà không cần converter. */
    private static Duration duration(ConfigurableEnvironment environment, String key, Duration fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : DurationStyle.detectAndParse(value.trim());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
