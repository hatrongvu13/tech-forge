package com.htv.starter.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thuộc tính {@code htv.core.*}. */
@ConfigurationProperties(prefix = "htv.core")
public class CoreProperties {

    /** Tắt toàn bộ core-starter. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
