package com.htv.starter.feign;

import static org.assertj.core.api.Assertions.assertThat;

import feign.codec.ErrorDecoder;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class FeignAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class));

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(basePackages = "com.htv.nothing.here")
    static class UserEnables {
    }

    @Test
    void registersInterceptorDecoderAndScanning() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ContextRequestInterceptor.class).hasSingleBean(ErrorDecoder.class);
            assertThat(ctx).hasSingleBean(FeignAutoConfiguration.ScanFeignClients.class);
        });
    }

    @Test
    void backsOffScanningWhenTheServiceDeclaresEnableFeignClients() {
        runner.withUserConfiguration(UserEnables.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(FeignAutoConfiguration.ScanFeignClients.class));
    }

    @Test
    void canBeDisabledAndOverridden() {
        runner.withPropertyValues("htv.feign.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ContextRequestInterceptor.class));
        ErrorDecoder mine = (k, r) -> new IllegalStateException();
        runner.withBean(ErrorDecoder.class, () -> mine)
                .run(ctx -> assertThat(ctx.getBean(ErrorDecoder.class)).isSameAs(mine));
    }

    @Test
    void timeoutsDefaultLowAndServiceSettingsWin() {
        StandardEnvironment env = new StandardEnvironment();
        new FeignEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.connect-timeout")).isEqualTo("2000");
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.read-timeout")).isEqualTo("5000");

        StandardEnvironment custom = new StandardEnvironment();
        custom.getPropertySources().addFirst(new MapPropertySource("app", Map.of(
                "htv.feign.read-timeout", "1s",
                "spring.cloud.openfeign.client.config.default.connect-timeout", "999")));
        new FeignEnvironmentPostProcessor().postProcessEnvironment(custom, new SpringApplication());
        assertThat(custom.getProperty("spring.cloud.openfeign.client.config.default.read-timeout")).isEqualTo("1000");
        assertThat(custom.getProperty("spring.cloud.openfeign.client.config.default.connect-timeout")).isEqualTo("999");

        StandardEnvironment off = new StandardEnvironment();
        off.getPropertySources().addFirst(new MapPropertySource("app", Map.of("htv.feign.enabled", "false")));
        new FeignEnvironmentPostProcessor().postProcessEnvironment(off, new SpringApplication());
        assertThat(off.getProperty("spring.cloud.openfeign.client.config.default.read-timeout")).isNull();
    }
}
