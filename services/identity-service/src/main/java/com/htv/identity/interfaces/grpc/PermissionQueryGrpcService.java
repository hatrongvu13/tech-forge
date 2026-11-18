package com.htv.identity.interfaces.grpc;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.GetUserPermissionsResponse;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.identity.application.usecase.GetUserPermissionsUseCase;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Cổng gRPC cho service khác hỏi quyền khi cache Redis không có. Lỗi nghiệp vụ được
 * {@code GrpcExceptionInterceptor} của grpc-starter dịch thành status chuẩn.
 */
@Component
public class PermissionQueryGrpcService extends PermissionQueryServiceGrpc.PermissionQueryServiceImplBase {

    private final GetUserPermissionsUseCase useCase;

    public PermissionQueryGrpcService(GetUserPermissionsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void getUserPermissions(GetUserPermissionsRequest request,
                                   StreamObserver<GetUserPermissionsResponse> responseObserver) {
        UUID userId = parse(request.getUserId());
        Set<String> permissions = new TreeSet<>(useCase.execute(userId));
        responseObserver.onNext(GetUserPermissionsResponse.newBuilder().addAllPermissions(permissions).build());
        responseObserver.onCompleted();
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, "user_id is not a UUID", e);
        }
    }
}
