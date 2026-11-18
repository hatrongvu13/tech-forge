package com.htv.starter.grpc;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hỏi identity-service quyền của người dùng qua gRPC. Fail-closed: mọi lỗi (kể cả identity không phản hồi)
 * thành {@code COMMON-5030}, tuyệt đối không trả tập rỗng giả.
 */
public class GrpcPermissionProvider implements PermissionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcPermissionProvider.class);

    private final PermissionQueryServiceGrpc.PermissionQueryServiceBlockingStub stub;

    public GrpcPermissionProvider(Channel identityChannel) {
        this.stub = PermissionQueryServiceGrpc.newBlockingStub(identityChannel);
    }

    @Override
    public Set<String> permissionsOf(UUID userId) {
        try {
            return Set.copyOf(stub.getUserPermissions(
                    GetUserPermissionsRequest.newBuilder().setUserId(userId.toString()).build())
                    .getPermissionsList());
        } catch (StatusRuntimeException e) {
            LOG.warn("Cannot load permissions from identity-service: {}", e.getStatus().getCode());
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                    "identity-service permission lookup failed: " + e.getStatus().getCode(), e);
        }
    }
}
