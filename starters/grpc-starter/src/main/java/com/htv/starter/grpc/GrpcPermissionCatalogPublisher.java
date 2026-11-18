package com.htv.starter.grpc;

import com.htv.contract.identity.v1.PermissionCatalogServiceGrpc;
import com.htv.contract.identity.v1.PermissionDef;
import com.htv.contract.identity.v1.RegisterRequest;
import com.htv.contract.identity.v1.RegisterResponse;
import com.htv.shared.auth.PermissionCatalog;
import com.htv.shared.auth.PermissionCatalogPublisher;
import io.grpc.Channel;

/** Gửi danh mục quyền tới identity-service qua gRPC. Lỗi được dịch thành {@code BusinessException} (xem {@link GrpcErrors}). */
public class GrpcPermissionCatalogPublisher implements PermissionCatalogPublisher {

    private final PermissionCatalogServiceGrpc.PermissionCatalogServiceBlockingStub stub;

    public GrpcPermissionCatalogPublisher(Channel identityChannel) {
        this.stub = PermissionCatalogServiceGrpc.newBlockingStub(identityChannel);
    }

    @Override
    public PublishResult publish(PermissionCatalog catalog) {
        RegisterRequest.Builder request = RegisterRequest.newBuilder()
                .setServiceName(catalog.serviceName())
                .setServiceVersion(catalog.serviceVersion())
                .setInstanceId(catalog.instanceId() == null ? "" : catalog.instanceId());
        catalog.permissions().forEach(p -> request.addPermissions(
                PermissionDef.newBuilder().setCode(p.code()).setDescription(p.description()).build()));

        RegisterResponse response = GrpcErrors.translate(() -> stub.register(request.build()));
        return new PublishResult(response.getCreated(), response.getUpdated(), response.getUnchanged(),
                response.getRejectedList().stream()
                        .map(r -> new RejectedCode(r.getCode(), r.getReason()))
                        .toList());
    }
}
