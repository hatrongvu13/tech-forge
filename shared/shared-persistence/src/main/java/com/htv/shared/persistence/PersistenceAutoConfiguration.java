package com.htv.shared.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Bật JPA auditing cho mọi service có module này, không cần cấu hình thêm. */
@AutoConfiguration
@ConditionalOnClass(EnableJpaAuditing.class)
@EnableJpaAuditing
public class PersistenceAutoConfiguration {
}
