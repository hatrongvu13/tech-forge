package com.htv.identity.infrastructure.config;

import com.htv.identity.application.port.PermissionCache;
import com.htv.identity.application.usecase.GetUserPermissionsUseCase;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase;
import com.htv.identity.domain.IdentityErrorCode;
import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.BusinessException;
import com.htv.starter.cached.PermissionCacheStore;
import java.time.Clock;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityCatalogProperties.class)
public class IdentityConfiguration {

    IdentityConfiguration(IdentityCatalogProperties catalogProperties, Environment environment) {
        catalogProperties.validate(environment);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Ghi/xóa cache quyền trên Redis qua {@link PermissionCacheStore} của cached-starter. */
    @Bean
    PermissionCache permissionCache(PermissionCacheStore store) {
        return new PermissionCache() {
            @Override
            public void put(UUID userId, Set<String> permissions) {
                store.put(userId, permissions);
            }

            @Override
            public void evict(UUID userId) {
                store.evict(userId);
            }

            @Override
            public void evictAll(Collection<UUID> userIds) {
                store.evictAll(userIds);
            }
        };
    }

    /**
     * Identity là nguồn quyền nên tự tính từ DB, không đi vòng qua Redis/gRPC của chính mình.
     * Khai báo bean này thì {@code redisPermissionProvider} của cached-starter tự lùi lại.
     * Người dùng không tồn tại có nghĩa là không có quyền nào.
     */
    @Bean
    PermissionProvider identityPermissionProvider(GetUserPermissionsUseCase useCase) {
        return userId -> {
            try {
                return useCase.execute(userId);
            } catch (BusinessException e) {
                if (e.getErrorCode() == IdentityErrorCode.USER_NOT_FOUND) {
                    return Set.of();
                }
                throw e;
            }
        };
    }

    /**
     * Identity tự đăng ký quyền của chính nó TRONG TIẾN TRÌNH, không gọi gRPC vào chính mình. Bean này thay bản gRPC
     * mặc định của grpc-starter.
     */
    @Bean
    PermissionCatalogPublisher localPermissionCatalogPublisher(RegisterPermissionCatalogUseCase useCase) {
        return catalog -> {
            var result = useCase.execute(new RegisterPermissionCatalogUseCase.Command(catalog.serviceName(),
                    catalog.serviceVersion(), catalog.instanceId(), catalog.permissions().stream()
                    .map(p -> new RegisterPermissionCatalogUseCase.Definition(p.code(), p.description())).toList()));
            return new PermissionCatalogPublisher.PublishResult(result.created(), result.updated(), result.unchanged(),
                    result.rejected().stream()
                            .map(r -> new PermissionCatalogPublisher.RejectedCode(r.code(), r.reason())).toList());
        };
    }
}
