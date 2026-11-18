package com.htv.starter.core;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thuộc tính {@code htv.core.*}. */
@ConfigurationProperties(prefix = "htv.core")
public class CoreProperties {

    /** Tắt toàn bộ core-starter. */
    private boolean enabled = true;

    private final Log log = new Log();

    public Log getLog() {
        return log;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static class Log {

        /**
         * Mẫu (biểu thức chính quy) cần che thêm khi ghi log, ngoài các mẫu có sẵn (Bearer, JWT, password=...).
         * Ví dụ che token trong đường dẫn: {@code /reset-password/[^/]+}.
         */
        private List<String> maskPatterns = new ArrayList<>();

        public List<String> getMaskPatterns() {
            return maskPatterns;
        }

        public void setMaskPatterns(List<String> maskPatterns) {
            this.maskPatterns = maskPatterns;
        }
    }
}
