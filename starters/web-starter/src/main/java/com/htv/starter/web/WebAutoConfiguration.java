package com.htv.starter.web;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Cấu hình REST cho service servlet. Tắt bằng {@code htv.web.enabled=false}.
 * Thứ tự filter: RequestContext trước, AccessLog sau, cả hai chạy trước Spring Security
 * (để log được cả 401/403 và có traceId).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "htv.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebProperties.class)
public class WebAutoConfiguration {

    public static final int REQUEST_CONTEXT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int ACCESS_LOG_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "htv.web", name = "trust-user-header", havingValue = "true")
    @ConditionalOnMissingBean(name = "headerUserIdResolver")
    UserIdResolver headerUserIdResolver() {
        return new HeaderUserIdResolver();
    }

    @Bean
    @ConditionalOnMissingBean(name = "htvRequestContextFilter")
    FilterRegistrationBean<RequestContextFilter> htvRequestContextFilter(ObjectProvider<UserIdResolver> resolvers) {
        List<UserIdResolver> list = resolvers.orderedStream().toList();
        FilterRegistrationBean<RequestContextFilter> bean = new FilterRegistrationBean<>(new RequestContextFilter(list));
        bean.setOrder(REQUEST_CONTEXT_FILTER_ORDER);
        return bean;
    }

    @Bean
    @ConditionalOnProperty(prefix = "htv.web.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "htvAccessLogFilter")
    FilterRegistrationBean<AccessLogFilter> htvAccessLogFilter(WebProperties properties) {
        FilterRegistrationBean<AccessLogFilter> bean =
                new FilterRegistrationBean<>(new AccessLogFilter(properties.getAccessLog().getExcludePaths()));
        bean.setOrder(ACCESS_LOG_FILTER_ORDER);
        return bean;
    }
}
