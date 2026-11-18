package com.htv.starter.grpc;

import io.grpc.ClientInterceptor;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Tạo và giữ kênh gRPC theo tên ({@code htv.grpc.client.channels.<tên>.address}). Mỗi kênh đã gắn sẵn
 * interceptor ngữ cảnh và deadline mặc định. Dùng: {@code OrderServiceGrpc.newBlockingStub(factory.channel("order"))}.
 */
public class GrpcChannelFactory implements DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcChannelFactory.class);

    private final GrpcProperties.Client properties;
    private final List<ClientInterceptor> interceptors;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public GrpcChannelFactory(GrpcProperties.Client properties, List<ClientInterceptor> interceptors) {
        this.properties = properties;
        this.interceptors = List.copyOf(interceptors);
    }

    public boolean has(String name) {
        GrpcProperties.Target t = properties.getChannels().get(name);
        return t != null && t.getAddress() != null && !t.getAddress().isBlank();
    }

    public ManagedChannel channel(String name) {
        return channels.computeIfAbsent(name, this::create);
    }

    private ManagedChannel create(String name) {
        GrpcProperties.Target target = properties.getChannels().get(name);
        if (target == null || target.getAddress() == null || target.getAddress().isBlank()) {
            throw new IllegalStateException("No address for gRPC channel '" + name
                    + "'. Set htv.grpc.client.channels." + name + ".address");
        }
        ManagedChannelBuilder<?> builder = Grpc.newChannelBuilder(target.getAddress(),
                GrpcTls.client(name, target.getTls()));
        if (target.getLoadBalancingPolicy() != null && !target.getLoadBalancingPolicy().isBlank()) {
            builder.defaultLoadBalancingPolicy(target.getLoadBalancingPolicy());
        }
        builder.intercept(interceptors);
        LOG.info("gRPC channel '{}' -> {}", name, target.getAddress());
        return builder.build();
    }

    @Override
    public void destroy() {
        channels.values().forEach(ManagedChannel::shutdown);
        channels.values().forEach(c -> {
            try {
                if (!c.awaitTermination(5, TimeUnit.SECONDS)) {
                    c.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                c.shutdownNow();
            }
        });
        channels.clear();
    }
}
