package com.htv.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.htv.shared.auth.PermissionCatalog;
import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionCatalogPublisher.PublishResult;
import com.htv.shared.auth.PermissionCatalogPublisher.RejectedCode;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

class PermissionCatalogSyncerTest {

    private static final PublishResult OK = new PublishResult(2, 0, 0, List.of());

    @Component
    static class Declares {
        @RequirePermission(value = "order:read", description = "Read")
        void a() { }

        @RequirePermission("order:cancel")
        void b() { }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        RequirePermissionScanner scanner(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf,
                                         org.springframework.beans.factory.ObjectProvider<PermissionProvider> p) {
            return new RequirePermissionScanner(bf, p);
        }
    }

    /** Scanner đã quét xong với 2 quyền khai báo. */
    private static RequirePermissionScanner scannerWithTwoCodes() {
        RequirePermissionScanner[] holder = new RequirePermissionScanner[1];
        new ApplicationContextRunner().withUserConfiguration(Config.class)
                .withBean("provider", PermissionProvider.class, () -> id -> java.util.Set.of())
                .withBean(Declares.class)
                .run(ctx -> holder[0] = ctx.getBean(RequirePermissionScanner.class));
        return holder[0];
    }

    private final RequirePermissionScanner scanner = scannerWithTwoCodes();
    private final PermissionCatalogPublisher publisher = mock(PermissionCatalogPublisher.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

    private PermissionCatalogSyncer syncer(boolean failFast) {
        return new PermissionCatalogSyncer(scanner, publisher, scheduler, "order-service", "1.2.3", "host-1",
                failFast, Duration.ofSeconds(1), Duration.ofSeconds(8));
    }

    /** Lấy tác vụ đã được đưa vào executor (lần thử đầu) và chạy nó. */
    private void runFirstAttempt() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).execute(task.capture());
        task.getValue().run();
    }

    private Runnable captureRetry(long expectedDelayMs) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, org.mockito.Mockito.atLeastOnce()).schedule(task.capture(), eq(expectedDelayMs),
                eq(TimeUnit.MILLISECONDS));
        return task.getValue();
    }

    @Test
    void doesNotBlockStartupAndPublishesTheScannedCatalog() {
        when(OK);
        PermissionCatalogSyncer s = syncer(false);
        s.start();
        verify(publisher, never()).publish(any()); // chưa chạy: chỉ đưa vào executor

        runFirstAttempt();

        ArgumentCaptor<PermissionCatalog> sent = ArgumentCaptor.forClass(PermissionCatalog.class);
        verify(publisher).publish(sent.capture());
        assertThat(sent.getValue().serviceName()).isEqualTo("order-service");
        assertThat(sent.getValue().serviceVersion()).isEqualTo("1.2.3");
        assertThat(sent.getValue().instanceId()).isEqualTo("host-1");
        assertThat(sent.getValue().permissions()).containsExactlyInAnyOrder(
                new PermissionDef("order:read", "Read"), new PermissionDef("order:cancel", ""));
        assertThat(s.isDone()).isTrue();
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void retriesWithDoublingDelayCappedAtMaxUntilItSucceeds() {
        List<Object> outcomes = new ArrayList<>(List.of(
                new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE),
                new BusinessException(CommonErrorCode.TIMEOUT),
                new IllegalStateException("unexpected"),
                new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE),
                new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE),
                OK));
        org.mockito.Mockito.when(publisher.publish(any())).thenAnswer(inv -> {
            Object o = outcomes.remove(0);
            if (o instanceof RuntimeException e) {
                throw e;
            }
            return o;
        });
        PermissionCatalogSyncer s = syncer(false);
        s.start();
        runFirstAttempt();
        // 1s, 2s, 4s, 8s, rồi giữ ở 8s (trần)
        for (long delay : new long[] {1000, 2000, 4000, 8000, 8000}) {
            assertThat(s.isDone()).isFalse();
            captureRetry(delay).run();
        }
        assertThat(s.isDone()).isTrue();
        verify(publisher, times(6)).publish(any());
    }

    @Test
    void stopsRetryingOnRefusalThatRetryingCannotFix() {
        org.mockito.Mockito.when(publisher.publish(any())).thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));
        PermissionCatalogSyncer s = syncer(false);
        s.start();
        runFirstAttempt();
        assertThat(s.isDone()).isTrue();
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void rejectedCodesAreOnlyLoggedInNormalMode() {
        org.mockito.Mockito.when(publisher.publish(any()))
                .thenReturn(new PublishResult(1, 0, 0, List.of(new RejectedCode("order:read", "OWNED_BY_OTHER"))));
        PermissionCatalogSyncer s = syncer(false);
        s.start();
        runFirstAttempt();
        assertThat(s.isDone()).isTrue();
    }

    @Test
    void destroyStopsFurtherAttempts() {
        PermissionCatalogSyncer s = syncer(false);
        s.start();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).execute(task.capture());
        s.destroy();
        task.getValue().run();
        verify(publisher, never()).publish(any());
        verify(scheduler).shutdownNow();
    }

    // ------------------------------------------------------------------- fail-fast

    @Test
    void failFastPublishesSynchronouslyAndPropagatesFailure() {
        org.mockito.Mockito.when(publisher.publish(any())).thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> syncer(true).start()).isInstanceOf(BusinessException.class);
        verify(scheduler, never()).execute(any());
    }

    @Test
    void failFastFailsWhenAnyCodeIsRejected() {
        org.mockito.Mockito.when(publisher.publish(any()))
                .thenReturn(new PublishResult(1, 0, 0, List.of(new RejectedCode("order:read", "OWNED_BY_OTHER"))));
        assertThatThrownBy(() -> syncer(true).start()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("order:read");
    }

    @Test
    void failFastSucceedsQuietly() {
        when(OK);
        PermissionCatalogSyncer s = syncer(true);
        s.start();
        assertThat(s.isDone()).isTrue();
    }

    private void when(PublishResult result) {
        org.mockito.Mockito.when(publisher.publish(any())).thenReturn(result);
    }
}
