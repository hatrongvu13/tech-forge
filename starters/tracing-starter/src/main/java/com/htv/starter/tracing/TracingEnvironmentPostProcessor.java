package com.htv.starter.tracing;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Mặc định vận hành ở mức ưu tiên THẤP NHẤT ({@code application.yml} của service luôn thắng):
 *
 * <ul>
 *   <li>Actuator chạy ở <b>cổng quản trị riêng</b> ({@code 8081}), tách khỏi cổng API. Cổng này không đưa ra
 *       Ingress/Service công khai; chỉ kubelet (probe) và Prometheus gọi vào.
 *   <li>Chỉ mở {@code health}, {@code info}, {@code prometheus}.
 *   <li>Bật probe Kubernetes: {@code /actuator/health/liveness} và {@code /actuator/health/readiness}.
 *   <li>Không trả chi tiết health (tên DB, phiên bản, lỗi kết nối).
 *   <li>Lấy mẫu trace: 100% ngoài {@code prod}, 10% ở {@code prod}.
 *   <li>Tắt êm: chờ request đang chạy tối đa 30 giây khi Kubernetes dừng pod.
 * </ul>
 */
public class TracingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "htvTracingDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("htv.tracing.enabled", Boolean.class, true)) {
            return;
        }
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("management.server.port", environment.getProperty("htv.tracing.management-port", "8081"));
        defaults.put("management.endpoints.web.exposure.include", "health,info,prometheus");
        defaults.put("management.endpoint.health.probes.enabled", "true");
        defaults.put("management.endpoint.health.show-details", "never");
        defaults.put("management.endpoint.health.show-components", "never");
        defaults.put("management.tracing.sampling.probability", prod ? "0.1" : "1.0");
        defaults.put("management.observations.key-values.application",
                environment.getProperty("spring.application.name", "unknown"));
        defaults.put("server.shutdown", "graceful");
        defaults.put("spring.lifecycle.timeout-per-shutdown-phase", "30s");
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
