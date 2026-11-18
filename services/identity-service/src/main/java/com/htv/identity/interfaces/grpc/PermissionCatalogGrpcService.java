package com.htv.identity.interfaces.grpc;

import com.htv.contract.identity.v1.PermissionCatalogServiceGrpc;
import com.htv.contract.identity.v1.RegisterRequest;
import com.htv.contract.identity.v1.RegisterResponse;
import com.htv.contract.identity.v1.RejectedCode;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Command;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Definition;
import com.htv.identity.application.usecase.RegisterPermissionCatalogUseCase.Result;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import com.htv.starter.core.ContextValues;
import com.htv.starter.grpc.GrpcPeers;
import io.grpc.stub.StreamObserver;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Cổng gRPC cho service đăng ký danh mục quyền.
 *
 * <p>Tên service lấy từ CN của chứng chỉ mTLS, KHÔNG tin giá trị trong thân yêu cầu: service A không thể đăng ký
 * hay vô hiệu hóa quyền của service B. Chỉ ở dev (không có mTLS) mới cho dùng tên trong thân, và phải bật
 * {@code htv.identity.catalog.trust-request-body=true} (bị cấm ở profile {@code prod}).
 */
@Component
@EnableConfigurationProperties(IdentityCatalogProperties.class)
public class PermissionCatalogGrpcService extends PermissionCatalogServiceGrpc.PermissionCatalogServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionCatalogGrpcService.class);

    private final RegisterPermissionCatalogUseCase useCase;
    private final IdentityCatalogProperties properties;

    public PermissionCatalogGrpcService(RegisterPermissionCatalogUseCase useCase,
                                        IdentityCatalogProperties properties, Environment environment) {
        properties.validate(environment); // cờ dev bị cấm ở prod: không khởi động
        this.useCase = useCase;
        this.properties = properties;
    }

    @Override
    public void register(RegisterRequest request, StreamObserver<RegisterResponse> out) {
        String caller = resolveCaller(request.getServiceName(), GrpcPeers.serviceName());
        Result result = useCase.execute(new Command(caller, request.getServiceVersion(), request.getInstanceId(),
                request.getPermissionsList().stream()
                        .map(p -> new Definition(p.getCode(), p.getDescription())).toList()));
        out.onNext(RegisterResponse.newBuilder()
                .setCreated(result.created()).setUpdated(result.updated()).setUnchanged(result.unchanged())
                .addAllRejected(result.rejected().stream()
                        .map(r -> RejectedCode.newBuilder().setCode(r.code()).setReason(r.reason()).build()).toList())
                .build());
        out.onCompleted();
    }

    String resolveCaller(String claimedName, Optional<String> certificateName) {
        if (certificateName.isPresent()) {
            String cert = certificateName.get();
            if (!claimedName.isBlank() && !claimedName.equals(cert)) {
                LOG.warn("Service '{}' tried to register a catalog as '{}'", cert, ContextValues.safe(claimedName));
                throw new BusinessException(CommonErrorCode.FORBIDDEN, "service_name does not match the certificate");
            }
            return cert;
        }
        if (properties.isTrustRequestBody()) {
            if (claimedName.isBlank()) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "service_name is required");
            }
            return claimedName;
        }
        throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "client certificate required");
    }
}
