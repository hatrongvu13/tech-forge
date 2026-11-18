package com.htv.starter.feign;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thuộc tính {@code htv.feign.*}. Cấu hình Feign chuẩn vẫn dùng {@code spring.cloud.openfeign.*}. */
@ConfigurationProperties(prefix = "htv.feign")
public class FeignProperties {

    /** Tắt toàn bộ feign-starter. */
    private boolean enabled = true;

    /**
     * Tên các Feign client ({@code @FeignClient(name = ...)}) là service NỘI BỘ: được nhận {@code Authorization}
     * và {@code X-User-Id} của request hiện tại. Client khác (API bên ngoài) chỉ nhận traceId và channel,
     * để không lộ token người dùng ra ngoài.
     */
    private List<String> trustedClients = new ArrayList<>();

    /** Timeout kết nối mặc định cho mọi client (ghi đè bằng {@code spring.cloud.openfeign.client.config.*}). */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** Timeout đọc mặc định cho mọi client. */
    private Duration readTimeout = Duration.ofSeconds(5);

    /** Số byte tối đa đọc từ thân phản hồi lỗi để tìm mã lỗi. */
    private int maxErrorBodyBytes = 8192;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getTrustedClients() {
        return trustedClients;
    }

    public void setTrustedClients(List<String> trustedClients) {
        this.trustedClients = trustedClients;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxErrorBodyBytes() {
        return maxErrorBodyBytes;
    }

    public void setMaxErrorBodyBytes(int maxErrorBodyBytes) {
        this.maxErrorBodyBytes = maxErrorBodyBytes;
    }
}
