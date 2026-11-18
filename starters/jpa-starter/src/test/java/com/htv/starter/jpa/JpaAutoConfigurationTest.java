package com.htv.starter.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.AuditorAware;

/** Mẫu test khởi động cho starter đơn: bean có mặt, ghi đè được, tắt được. */
class JpaAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JpaAutoConfiguration.class));

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
    }

    @Test
    void registersAuditorAwareBackedByContextHolder() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(AuditorAware.class);
            @SuppressWarnings("unchecked")
            AuditorAware<String> auditor = ctx.getBean(AuditorAware.class);
            assertThat(auditor.getCurrentAuditor()).isEmpty();
            try (ContextHolder.Scope ignored = ContextHolder.open(RequestContext.builder().userId("u-1").build())) {
                assertThat(auditor.getCurrentAuditor()).contains("u-1");
            }
            try (ContextHolder.Scope ignored = ContextHolder.open(RequestContext.builder().userId(" ").build())) {
                assertThat(auditor.getCurrentAuditor()).isEmpty();
            }
        });
    }

    @Test
    void doesNotEnableAuditingWithoutEntityManagerFactory() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("jpaAuditingHandler"));
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("htv.jpa.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditorAware.class));
    }

    @Test
    void serviceCanOverrideAuditor() {
        runner.withBean("custom", AuditorAware.class, () -> () -> java.util.Optional.of("system"))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AuditorAware.class);
                    assertThat(ctx.getBean(AuditorAware.class).getCurrentAuditor()).contains("system");
                });
    }
}
