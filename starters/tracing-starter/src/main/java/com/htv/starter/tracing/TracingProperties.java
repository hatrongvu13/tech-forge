package com.htv.starter.tracing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Thuộc tính {@code htv.tracing.*}. Giá trị được {@link TracingEnvironmentPostProcessor} đọc lúc khởi động;
 * lớp này chỉ để khai báo metadata (IDE gợi ý khóa) và kiểm kiểu.
 */
@ConfigurationProperties(prefix = "htv.tracing")
public class TracingProperties {

    /** Tắt các mặc định vận hành của tracing-starter (dependency vẫn còn). */
    private boolean enabled = true;

    /** Cổng quản trị riêng của actuator (probe, Prometheus). Không đưa cổng này ra ngoài. */
    private int managementPort = 8081;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public void setManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }
}
