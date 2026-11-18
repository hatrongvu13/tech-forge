package com.htv.starter.security;

import com.htv.shared.auth.PermissionCatalog;
import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionCatalogPublisher.PublishResult;
import com.htv.shared.common.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * Gửi danh mục quyền của service (các {@code @RequirePermission} đã quét) tới identity-service khi service khởi
 * động xong, để quản trị viên chọn được quyền khi gán cho role (xem thiết kế mục 3.5).
 *
 * <ul>
 *   <li>Chạy nền, <b>không chặn</b> khởi động. Identity chưa sẵn sàng thì thử lại với khoảng chờ tăng dần
 *       (mặc định 1 giây đến tối đa 5 phút) cho đến khi thành công. Quyền đang chạy không bị ảnh hưởng.
 *   <li>Chỉ thử lại với lỗi phía hệ thống (HTTP 5xx). Lỗi do yêu cầu hoặc phân quyền (ví dụ chứng chỉ không khớp
 *       tên service) thì dừng và báo ERROR, vì thử lại cũng không đổi kết quả.
 *   <li>{@code failFast=true}: thử đồng bộ ngay, thất bại hoặc có mã bị từ chối thì service không khởi động.
 *   <li>Chỉ đăng ký một lần mỗi lần khởi động, không định kỳ.
 * </ul>
 */
public class PermissionCatalogSyncer implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionCatalogSyncer.class);

    private final RequirePermissionScanner scanner;
    private final PermissionCatalogPublisher publisher;
    private final ScheduledExecutorService scheduler;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;
    private final boolean failFast;
    private final Duration initialDelay;
    private final Duration maxDelay;

    private volatile boolean stopped;
    private volatile boolean done;

    public PermissionCatalogSyncer(RequirePermissionScanner scanner, PermissionCatalogPublisher publisher,
                                   ScheduledExecutorService scheduler, String serviceName, String serviceVersion,
                                   String instanceId, boolean failFast, Duration initialDelay, Duration maxDelay) {
        this.scanner = scanner;
        this.publisher = publisher;
        this.scheduler = scheduler;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
        this.failFast = failFast;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        start();
    }

    /** Bắt đầu đồng bộ (gọi khi ứng dụng sẵn sàng). Công khai để test tích hợp điều khiển được. */
    public void start() {
        if (failFast) {
            PublishResult result = publishOnce(); // ném khi thất bại
            checkRejected(result);
            done = true;
            return;
        }
        scheduler.execute(() -> attempt(initialDelay));
    }

    /** True khi đã đăng ký thành công (hoặc đã dừng vì lỗi không thử lại được). */
    boolean isDone() {
        return done;
    }

    private void attempt(Duration nextDelay) {
        if (stopped || done) {
            return;
        }
        try {
            PublishResult result = publishOnce();
            done = true;
            for (var r : result.rejected()) {
                LOG.warn("Permission code '{}' of {} was rejected: {}", r.code(), serviceName, r.reason());
            }
        } catch (RuntimeException e) {
            if (isRetryable(e)) {
                LOG.warn("Cannot register permission catalog of {} (will retry in {}): {}", serviceName, nextDelay,
                        describe(e));
                Duration following = nextDelay.multipliedBy(2).compareTo(maxDelay) > 0 ? maxDelay : nextDelay.multipliedBy(2);
                scheduler.schedule(() -> attempt(following), nextDelay.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                done = true;
                LOG.error("Permission catalog of {} was refused and will not be retried: {}", serviceName, describe(e));
            }
        }
    }

    private PublishResult publishOnce() {
        List<PermissionCatalog.PermissionDef> declared = scanner.declared();
        PublishResult result = publisher.publish(new PermissionCatalog(serviceName, serviceVersion, instanceId, declared));
        LOG.info("Registered {} permission code(s) of {}: created={} updated={} unchanged={} rejected={}",
                declared.size(), serviceName, result.created(), result.updated(), result.unchanged(),
                result.rejected().size());
        return result;
    }

    private void checkRejected(PublishResult result) {
        if (!result.rejected().isEmpty()) {
            throw new IllegalStateException("identity-service rejected permission code(s) of " + serviceName + ": "
                    + result.rejected());
        }
    }

    private static boolean isRetryable(RuntimeException e) {
        return !(e instanceof BusinessException be) || be.getErrorCode().httpStatus() >= 500;
    }

    private static String describe(RuntimeException e) {
        return e instanceof BusinessException be ? be.getErrorCode().code() : e.getClass().getSimpleName();
    }

    @Override
    public void destroy() {
        stopped = true;
        scheduler.shutdownNow();
    }
}
