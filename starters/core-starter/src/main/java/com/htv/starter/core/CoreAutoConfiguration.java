package com.htv.starter.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/** Cấu hình nền. Tắt bằng {@code htv.core.enabled=false}. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "htv.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

    /** Nạp mẫu che log của service. Mẫu sai cú pháp làm service không khởi động. */
    CoreAutoConfiguration(CoreProperties properties) {
        LogMasker.configure(properties.getLog().getMaskPatterns());
    }

    /** Spring Boot tự gắn bean {@link TaskDecorator} vào executor mặc định của {@code @Async}. */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
