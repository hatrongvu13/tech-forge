package com.htv.starter.grpc;

import io.grpc.BindableService;
import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Chạy gRPC server cùng vòng đời Spring: nhận mọi bean {@link BindableService}, gắn chuỗi interceptor,
 * thêm health service. Khi tắt: báo NOT_SERVING, ngừng nhận lời gọi mới, chờ lời gọi đang chạy.
 */
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final GrpcProperties.Server properties;
    private final List<BindableService> services;
    private final List<ServerInterceptor> interceptorsInExecutionOrder;
    private final HealthStatusManager health;

    private volatile Server server;

    /**
     * @param interceptorsInExecutionOrder interceptor theo thứ tự chạy (phần tử đầu chạy trước)
     */
    public GrpcServerLifecycle(GrpcProperties.Server properties, List<BindableService> services,
                               List<ServerInterceptor> interceptorsInExecutionOrder, HealthStatusManager health) {
        this.properties = properties;
        this.services = List.copyOf(services);
        this.interceptorsInExecutionOrder = List.copyOf(interceptorsInExecutionOrder);
        this.health = health;
    }

    @Override
    public synchronized void start() {
        if (server != null) {
            return;
        }
        ServerBuilder<?> builder = Grpc.newServerBuilderForPort(properties.getPort(), GrpcTls.server(properties.getTls()))
                .maxInboundMessageSize((int) properties.getMaxInboundMessageSize().toBytes());
        // ServerInterceptors.intercept: interceptor đứng CUỐI danh sách được gọi TRƯỚC, nên đảo thứ tự.
        List<ServerInterceptor> reversed = new ArrayList<>(interceptorsInExecutionOrder);
        Collections.reverse(reversed);
        for (BindableService service : services) {
            builder.addService(ServerInterceptors.intercept(service, reversed));
        }
        builder.addService(health.getHealthService());
        if (properties.isReflection()) {
            builder.addService(ProtoReflectionServiceV1.newInstance());
        }
        try {
            server = builder.build().start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start gRPC server on port " + properties.getPort(), e);
        }
        health.setStatus(HealthStatusManager.SERVICE_NAME_ALL_SERVICES,
                io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING);
        LOG.info("gRPC server started on port {} ({} service(s), mTLS={}, trustUserMetadata={})", getPort(),
                services.size(), properties.getTls().getClientAuth() == GrpcProperties.ClientAuth.REQUIRE,
                properties.effectiveTrustUserMetadata());
    }

    @Override
    public synchronized void stop() {
        Server s = server;
        if (s == null) {
            return;
        }
        health.enterTerminalState();
        s.shutdown();
        try {
            if (!s.awaitTermination(properties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("gRPC server did not stop within {}, forcing shutdown", properties.getShutdownTimeout());
                s.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            s.shutdownNow();
        }
        server = null;
        LOG.info("gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        Server s = server;
        return s != null && !s.isShutdown();
    }

    /** Cổng thật đang nghe (khác cấu hình khi cấu hình là 0). */
    public int getPort() {
        Server s = server;
        return s == null ? -1 : s.getPort();
    }

    /** Tắt sau các thành phần HTTP/Web (phase lớn hơn = dừng sau, khởi động trước). */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1024;
    }
}
