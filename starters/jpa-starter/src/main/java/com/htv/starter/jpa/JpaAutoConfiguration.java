package com.htv.starter.jpa;

import com.htv.shared.common.ContextHolder;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật JPA auditing. {@code created_by}/{@code updated_by} lấy từ {@link ContextHolder}
 * (userId của request hiện tại). Tắt bằng {@code htv.jpa.enabled=false}.
 *
 * <p>Auditing chỉ bật khi đã có {@link EntityManagerFactory}, nên service không có DB
 * (hoặc test không dựng DB) không bị lỗi khởi động.
 */
@AutoConfiguration(afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(EnableJpaAuditing.class)
@ConditionalOnProperty(prefix = "htv.jpa", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditorAware.class)
    AuditorAware<String> contextAuditorAware() {
        return () -> ContextHolder.userId().filter(id -> !id.isBlank());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(EntityManagerFactory.class)
    @EnableJpaAuditing
    static class AuditingConfiguration {
    }
}
