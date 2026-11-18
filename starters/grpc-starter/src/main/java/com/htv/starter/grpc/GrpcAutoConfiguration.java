package com.htv.starter.grpc;

import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionProvider;
import io.grpc.BindableService;
import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.grpc.protobuf.services.HealthStatusManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình gRPC. Tắt bằng {@code htv.grpc.enabled=false}.
 *
 * <p>Thứ tự interceptor phía server (ngoài vào trong): danh tính service gọi (mTLS), ngữ cảnh, access log, xử lý lỗi, rồi các bean
 * {@link ServerInterceptor} của service/starter khác (theo {@code @Order}), rồi handler.
 * Phía client: ngữ cảnh, deadline, rồi các bean {@link ClientInterceptor} khác.
 */
@AutoConfiguration
@ConditionalOnClass(io.grpc.Server.class)
@ConditionalOnProperty(prefix = "htv.grpc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GrpcProperties.class)
public class GrpcAutoConfiguration {

    static final String IDENTITY_CHANNEL = "identity";

    // ------------------------------------------------------------------ server

    @Bean
    @ConditionalOnMissingBean
    HealthStatusManager grpcHealthStatusManager() {
        return new HealthStatusManager();
    }

    @Bean
    @ConditionalOnBean(BindableService.class)
    @ConditionalOnMissingBean
    GrpcServerLifecycle grpcServerLifecycle(GrpcProperties properties, ObjectProvider<BindableService> services,
                                            ObjectProvider<ServerInterceptor> customInterceptors,
                                            HealthStatusManager health) {
        List<ServerInterceptor> chain = new ArrayList<>();
        chain.add(new PeerIdentityInterceptor());
        chain.add(new ContextServerInterceptor(properties.getServer().effectiveTrustUserMetadata()));
        if (properties.getServer().getAccessLog().isEnabled()) {
            chain.add(new GrpcAccessLogInterceptor(properties.getServer().getAccessLog().getExcludeMethods()));
        }
        chain.add(new GrpcExceptionInterceptor());
        customInterceptors.orderedStream().forEach(chain::add);
        return new GrpcServerLifecycle(properties.getServer(), services.orderedStream().toList(), chain, health);
    }

    // ------------------------------------------------------------------ client

    @Bean
    @ConditionalOnMissingBean
    GrpcChannelFactory grpcChannelFactory(GrpcProperties properties,
                                          ObjectProvider<ClientInterceptor> customInterceptors) {
        List<ClientInterceptor> chain = new ArrayList<>();
        chain.add(new ContextClientInterceptor());
        chain.add(new DeadlineClientInterceptor(properties.getClient().getDefaultDeadline()));
        customInterceptors.orderedStream().forEach(chain::add);
        // ManagedChannelBuilder.intercept: interceptor đứng sau cùng được gọi trước, nên đảo để phần tử đầu chạy trước.
        java.util.Collections.reverse(chain);
        return new GrpcChannelFactory(properties.getClient(), chain);
    }

    // ------------------------------------------------- tra quyền / đăng ký quyền

    @Bean(name = "grpcPermissionProvider")
    @ConditionalOnProperty(prefix = "htv.grpc.client.channels.identity", name = "address")
    @ConditionalOnMissingBean(name = "grpcPermissionProvider")
    PermissionProvider grpcPermissionProvider(GrpcChannelFactory channels) {
        return new GrpcPermissionProvider(channels.channel(IDENTITY_CHANNEL));
    }

    @Bean
    @ConditionalOnProperty(prefix = "htv.grpc.client.channels.identity", name = "address")
    @ConditionalOnMissingBean(PermissionCatalogPublisher.class)
    PermissionCatalogPublisher grpcPermissionCatalogPublisher(GrpcChannelFactory channels) {
        return new GrpcPermissionCatalogPublisher(channels.channel(IDENTITY_CHANNEL));
    }
}
