package com.htv.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

class RequirePermissionScannerTest {

    @Configuration(proxyBeanMethods = false)
    static class ScannerConfig {
        @Bean
        RequirePermissionScanner scanner(ConfigurableListableBeanFactory bf, ObjectProvider<PermissionProvider> p) {
            return new RequirePermissionScanner(bf, p);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ScannerConfig.class);
    private static final PermissionProvider PROVIDER = id -> Set.of();

    @Component
    @RequirePermission(value = "order:read", description = "Read orders")
    static class ClassLevel {
        @RequirePermission("order:cancel")
        void cancel() {
        }

        void other() {
        }
    }

    @Component
    static class SameCodeElsewhere {
        @RequirePermission(value = "order:read", description = "Read orders")
        void a() {
        }

        @RequirePermission("order:read")
            // không mô tả: không xung đột
        void b() {
        }
    }

    @Component
    static class BadCode {
        @RequirePermission("Order Read")
        void x() {
        }
    }

    @Component
    static class WildcardCode {
        @RequirePermission("order:*")
            // ký tự đại diện chỉ dùng khi cấp quyền, không dùng khi khai báo
        void x() {
        }
    }

    @Component
    static class ConflictA {
        @RequirePermission(value = "billing:pay", description = "Pay invoices")
        void x() {
        }
    }

    @Component
    static class ConflictB {
        @RequirePermission(value = "billing:pay", description = "Make a payment")
        void x() {
        }
    }

    @Test
    void collectsAndMergesDeclaredCodes() {
        runner.withBean("provider", PermissionProvider.class, () -> PROVIDER)
                .withBean(ClassLevel.class).withBean(SameCodeElsewhere.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(RequirePermissionScanner.class).declared())
                            .containsExactlyInAnyOrder(
                                    new PermissionDef("order:read", "Read orders"),
                                    new PermissionDef("order:cancel", ""));
                });
    }

    @Test
    void noAnnotationsMeansNoProviderIsNeeded() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(RequirePermissionScanner.class).declared()).isEmpty();
        });
    }

    @Test
    void failsStartupWithGuidanceWhenThereIsNoProvider() {
        runner.withBean(ClassLevel.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("no PermissionProvider")
                    .hasMessageContaining("cached-starter").hasStackTraceContaining("grpc-starter");
        });
    }

    @Test
    void failsStartupOnInvalidOrWildcardCode() {
        runner.withBean("provider", PermissionProvider.class, () -> PROVIDER).withBean(BadCode.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("Order Read")
                    .hasMessageContaining("BadCode#x");
        });
        runner.withBean("provider", PermissionProvider.class, () -> PROVIDER).withBean(WildcardCode.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("order:*");
        });
    }

    @Test
    void failsStartupOnConflictingDescriptionsForTheSameCode() {
        runner.withBean("provider", PermissionProvider.class, () -> PROVIDER)
                .withBean(ConflictA.class).withBean(ConflictB.class).run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).hasStackTraceContaining("billing:pay")
                            .hasMessageContaining("conflicting");
                });
    }
}
