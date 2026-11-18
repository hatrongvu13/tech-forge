package com.htv.starter.cached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.htv.shared.auth.PermissionProvider;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class CacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void registersStoreAndPrimaryProviderWithDefaultTtl() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(PermissionCacheStore.class);
            assertThat(ctx).hasBean("redisPermissionProvider");
            assertThat(ctx.getBean(PermissionProvider.class)).isInstanceOf(RedisPermissionProvider.class);
            assertThat(ctx.getBean(CacheProperties.class).getPermissions().getTtl())
                    .isEqualTo(java.time.Duration.ofMinutes(10));
        });
    }

    @Test
    void doesNothingWithoutRedisTemplate() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PermissionCacheStore.class)
                        .doesNotHaveBean(PermissionProvider.class));
    }

    @Test
    void canBeDisabledEntirelyOrJustPermissions() {
        runner.withPropertyValues("htv.cache.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PermissionCacheStore.class)
                        .doesNotHaveBean(PermissionProvider.class));
        runner.withPropertyValues("htv.cache.permissions.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(PermissionCacheStore.class)
                        .doesNotHaveBean(PermissionProvider.class));
    }

    @Test
    void backsOffWhenServiceDeclaresItsOwnProvider() {
        PermissionProvider mine = id -> Set.of("x:y");
        runner.withBean("identityPermissionProvider", PermissionProvider.class, () -> mine).run(ctx -> {
            assertThat(ctx).doesNotHaveBean("redisPermissionProvider");
            assertThat(ctx.getBean(PermissionProvider.class)).isSameAs(mine);
            assertThat(ctx).hasSingleBean(PermissionCacheStore.class); // store vẫn có cho identity ghi cache
        });
    }

    @Test
    void usesGrpcProviderAsDelegateWithoutBackingOff() {
        PermissionProvider grpc = id -> Set.of("from:grpc");
        runner.withBean("grpcPermissionProvider", PermissionProvider.class, () -> grpc).run(ctx -> {
            assertThat(ctx).hasBean("redisPermissionProvider");
            PermissionProvider primary = ctx.getBean(PermissionProvider.class);
            assertThat(primary).isInstanceOf(RedisPermissionProvider.class);
            assertThat(ctx.getBean("grpcPermissionProvider")).isSameAs(grpc);
        });
    }

    @Test
    void ttlIsConfigurable() {
        runner.withPropertyValues("htv.cache.permissions.ttl=30s")
                .run(ctx -> assertThat(ctx.getBean(CacheProperties.class).getPermissions().getTtl())
                        .isEqualTo(java.time.Duration.ofSeconds(30)));
    }
}
