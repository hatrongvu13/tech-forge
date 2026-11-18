package com.htv.starter.cached;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thuộc tính {@code htv.cache.*}. Kết nối Redis dùng khóa chuẩn {@code spring.data.redis.*}. */
@ConfigurationProperties(prefix = "htv.cache")
public class CacheProperties {

    /** Tắt toàn bộ cached-starter. */
    private boolean enabled = true;

    private final Permissions permissions = new Permissions();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    public static class Permissions {

        /** Bật cài đặt đọc/ghi cache quyền. */
        private boolean enabled = true;

        /** Thời gian sống của khóa quyền. Là mức trễ tối đa khi việc xóa cache lúc đổi role thất bại. */
        private Duration ttl = Duration.ofMinutes(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }
}
