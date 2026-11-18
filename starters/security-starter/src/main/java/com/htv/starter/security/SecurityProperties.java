package com.htv.starter.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính {@code htv.security.*}. Cấu hình JWT dùng khóa chuẩn của Spring Boot
 * ({@code spring.security.oauth2.resourceserver.jwt.*}, gồm {@code issuer-uri}, {@code jwk-set-uri},
 * {@code public-key-location}, {@code audiences}).
 */
@ConfigurationProperties(prefix = "htv.security")
public class SecurityProperties {

    /**
     * Tắt toàn bộ security-starter.
     */
    private boolean enabled = true;

    /**
     * Đường dẫn (kiểu Ant) không cần đăng nhập. Các đường dẫn khác bắt buộc có JWT hợp lệ.
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health/**", "/actuator/info",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"));

    private final Catalog catalog = new Catalog();

    public Catalog getCatalog() {
        return catalog;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    /** Đồng bộ danh mục quyền lên identity-service khi khởi động. */
    public static class Catalog {

        private boolean enabled = true;

        /**
         * Thử đồng bộ ngay lúc khởi động; thất bại hoặc có mã bị từ chối thì service KHÔNG khởi động.
         * Bật cho CI/UAT; mặc định tắt để identity chưa sẵn sàng không làm sập service.
         */
        private boolean failFast = false;

        /** Version gửi lên. Để trống: lấy từ build-info, không có thì {@code unknown}. */
        private String serviceVersion;

        private Duration retryInitial = Duration.ofSeconds(1);
        private Duration retryMax = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }

        public String getServiceVersion() {
            return serviceVersion;
        }

        public void setServiceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
        }

        public Duration getRetryInitial() {
            return retryInitial;
        }

        public void setRetryInitial(Duration retryInitial) {
            this.retryInitial = retryInitial;
        }

        public Duration getRetryMax() {
            return retryMax;
        }

        public void setRetryMax(Duration retryMax) {
            this.retryMax = retryMax;
        }
    }
}
