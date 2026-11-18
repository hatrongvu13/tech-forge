package com.htv.starter.cached;

import com.htv.shared.auth.PermissionProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Cache quyền trên Redis. Tắt bằng {@code htv.cache.enabled=false}, hoặc riêng phần quyền bằng
 * {@code htv.cache.permissions.enabled=false}.
 *
 * <p>{@code afterName}: cần {@link StringRedisTemplate} đã được Spring Boot tạo.
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass({StringRedisTemplate.class, PermissionProvider.class})
@ConditionalOnProperty(prefix = "htv.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "htv.cache.permissions", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    PermissionCacheStore permissionCacheStore(StringRedisTemplate redis, CacheProperties properties) {
        return new PermissionCacheStore(redis, properties.getPermissions().getTtl());
    }

    /**
     * Bản mặc định của service: đọc Redis, thiếu thì hỏi bean {@code grpcPermissionProvider} (nếu có).
     * Lùi lại khi service tự khai báo một {@link PermissionProvider} riêng (trừ bản gRPC). Identity-service
     * làm vậy vì nó chính là nguồn quyền.
     */
    @Bean(name = "redisPermissionProvider")
    @Primary
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "htv.cache.permissions", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @Conditional(NoCustomPermissionProviderCondition.class)
    PermissionProvider redisPermissionProvider(StringRedisTemplate redis,
                                               @Qualifier("grpcPermissionProvider")
                                               ObjectProvider<PermissionProvider> grpc) {
        return new RedisPermissionProvider(redis, grpc.getIfAvailable());
    }
}
