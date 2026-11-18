package com.htv.starter.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính {@code htv.web.*}.
 */
@ConfigurationProperties(prefix = "htv.web")
public class WebProperties {

    /**
     * Tắt toàn bộ web-starter.
     */
    private boolean enabled = true;

    /**
     * Tin header {@code X-User-Id}. Mặc định tắt: chỉ bật khi service nằm sau gateway đã xóa header của client
     * và mạng chặn truy cập trực tiếp. Khi dùng JWT (security-starter) thì giữ tắt.
     */
    private boolean trustUserHeader = false;

    private final AccessLog accessLog = new AccessLog();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustUserHeader() {
        return trustUserHeader;
    }

    public void setTrustUserHeader(boolean trustUserHeader) {
        this.trustUserHeader = trustUserHeader;
    }

    public AccessLog getAccessLog() {
        return accessLog;
    }

    public static class AccessLog {

        /**
         * Ghi mỗi request một dòng.
         */
        private boolean enabled = true;

        /**
         * Đường dẫn (kiểu Ant) không ghi, ví dụ probe của Kubernetes.
         */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }
    }
}
