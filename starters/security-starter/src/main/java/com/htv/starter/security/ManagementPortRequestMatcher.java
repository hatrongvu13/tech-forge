package com.htv.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Khớp request đến trên <b>cổng quản trị riêng</b> của actuator (khác cổng API). Cổng này chỉ dành cho
 * probe Kubernetes và Prometheus, không đưa ra ngoài, nên không đòi JWT.
 *
 * <p>Đọc cổng thật lúc chạy ({@code local.management.port}, {@code local.server.port} do Spring Boot đặt sau khi
 * server mở cổng), nên dùng được cả khi cấu hình cổng {@code 0}. Khi actuator dùng chung cổng API thì không khớp
 * gì: lúc đó chỉ {@code htv.security.public-paths} được mở.
 */
class ManagementPortRequestMatcher implements RequestMatcher {

    private final Environment environment;

    ManagementPortRequestMatcher(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        Integer management = environment.getProperty("local.management.port", Integer.class);
        Integer server = environment.getProperty("local.server.port", Integer.class);
        return management != null && management > 0 && !management.equals(server)
                && request.getLocalPort() == management;
    }
}
