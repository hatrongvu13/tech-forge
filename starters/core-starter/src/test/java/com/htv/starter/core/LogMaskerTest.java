package com.htv.starter.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LogMaskerTest {

    private static final String JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.c2lnbmF0dXJlLXZhbHVl";

    @AfterEach
    void reset() {
        LogMasker.configure(List.of());
    }

    @Test
    void masksBearerTokensAndBareJwts() {
        assertThat(LogMasker.mask("Authorization: Bearer abc.def-ghi")).isEqualTo("Authorization: Bearer ***");
        assertThat(LogMasker.mask("token in path /x/" + JWT + "/y")).isEqualTo("token in path /x/***/y");
    }

    @Test
    void masksSecretKeyValuePairsInManyShapes() {
        assertThat(LogMasker.mask("login failed password=hunter2 user=bob")).isEqualTo("login failed password=*** user=bob");
        assertThat(LogMasker.mask("{\"password\":\"hunter2\",\"user\":\"bob\"}"))
                .isEqualTo("{\"password\":\"***\",\"user\":\"bob\"}");
        assertThat(LogMasker.mask("api-key: AKIA123 secret=s3 refresh_token=r1&otp=123456"))
                .isEqualTo("api-key: *** secret=*** refresh_token=***&otp=***");
        assertThat(LogMasker.mask("PWD=abc")).isEqualTo("PWD=***");
    }

    @Test
    void leavesHarmlessTextAlone() {
        assertThat(LogMasker.mask("order 42 not found for user 7")).isEqualTo("order 42 not found for user 7");
        assertThat(LogMasker.mask("/orders/123/items")).isEqualTo("/orders/123/items");
        assertThat(LogMasker.mask("tokenizer failed")).isEqualTo("tokenizer failed");
        assertThat(LogMasker.mask(null)).isNull();
        assertThat(LogMasker.mask("")).isEmpty();
    }

    @Test
    void customPatternsAreAppliedAndInvalidOnesFailFast() {
        LogMasker.configure(List.of("/reset-password/[^/]+", " "));
        assertThat(LogMasker.mask("/auth/reset-password/AbC123xyz")).isEqualTo("/auth***");
        assertThatThrownBy(() -> LogMasker.configure(List.of("([unclosed"))).isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void customPatternsComeFromConfigurationAndBadOnesStopStartup() {
        ApplicationContextRunner runner =
                new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));
        // dùng dạng có chỉ số (như danh sách YAML): dạng một dòng sẽ tách regex ở dấu phẩy
        runner.withPropertyValues("htv.core.log.mask-patterns[0]=card=\\d{12,19}").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(LogMasker.mask("pay card=4111111111111111 ok")).isEqualTo("pay *** ok");
        });
        runner.withPropertyValues("htv.core.log.mask-patterns[0]=([bad").run(ctx -> assertThat(ctx).hasFailed());
    }
}
