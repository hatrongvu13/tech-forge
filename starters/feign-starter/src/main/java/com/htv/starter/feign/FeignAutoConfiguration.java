package com.htv.starter.feign;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import java.util.HashSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Feign cho service. Tắt bằng {@code htv.feign.enabled=false}.
 *
 * <p>Bean {@link RequestInterceptor} và {@link ErrorDecoder} khai báo ở đây áp dụng cho MỌI Feign client.
 * Service không cần {@code @EnableFeignClients}: starter tự quét {@code @FeignClient} dưới gói {@code com.htv}.
 */
@AutoConfiguration(beforeName = "org.springframework.cloud.openfeign.FeignAutoConfiguration")
@ConditionalOnClass(FeignClient.class)
@ConditionalOnProperty(prefix = "htv.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeignProperties.class)
public class FeignAutoConfiguration {

    /** Gói gốc được quét. Mọi service của dự án nằm dưới gói này. */
    public static final String BASE_PACKAGE = "com.htv";

    @Bean
    @ConditionalOnMissingBean(ContextRequestInterceptor.class)
    ContextRequestInterceptor htvContextRequestInterceptor(FeignProperties properties) {
        return new ContextRequestInterceptor(new HashSet<>(properties.getTrustedClients()));
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    ErrorDecoder htvBusinessErrorDecoder(FeignProperties properties) {
        return new BusinessErrorDecoder(properties.getMaxErrorBodyBytes());
    }

    /** Tự quét khi service chưa tự khai báo {@code @EnableFeignClients} (tránh đăng ký client hai lần). */
    @Configuration(proxyBeanMethods = false)
    @Conditional(NoUserEnableFeignClientsCondition.class)
    @EnableFeignClients(basePackages = BASE_PACKAGE)
    static class ScanFeignClients {
    }
}
