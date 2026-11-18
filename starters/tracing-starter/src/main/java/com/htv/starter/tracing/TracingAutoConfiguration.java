package com.htv.starter.tracing;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Đăng ký {@link TracingProperties}. Các mặc định vận hành nằm ở {@link TracingEnvironmentPostProcessor}. */
@AutoConfiguration
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {
}
