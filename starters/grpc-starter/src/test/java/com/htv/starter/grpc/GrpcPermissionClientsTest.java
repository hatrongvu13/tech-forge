package com.htv.starter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.contract.identity.v1.PermissionCatalogServiceGrpc;
import com.htv.contract.identity.v1.RegisterRequest;
import com.htv.contract.identity.v1.RegisterResponse;
import com.htv.contract.identity.v1.RejectedCode;
import com.htv.shared.auth.PermissionCatalog;
import com.htv.shared.auth.PermissionCatalog.PermissionDef;
import com.htv.shared.auth.PermissionCatalogPublisher.PublishResult;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GrpcPermissionClientsTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void providerReturnsPermissionsFromIdentity() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            AtomicReference<String> askedFor = new AtomicReference<>();
            f.service.handler = r -> {
                askedFor.set(r.getUserId());
                return List.of("order:read", "billing:*");
            };
            Set<String> perms = new GrpcPermissionProvider(f.channel).permissionsOf(USER);
            assertThat(perms).containsExactlyInAnyOrder("order:read", "billing:*");
            assertThat(askedFor.get()).isEqualTo(USER.toString());
        }
    }

    @Test
    void providerReturnsEmptySetWhenUserHasNoPermission() throws Exception {
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            assertThat(new GrpcPermissionProvider(f.channel).permissionsOf(USER)).isEmpty();
        }
    }

    @Test
    void providerFailsClosedWhenIdentityFailsOrIsDown() throws Exception {
        GrpcPermissionProvider down;
        try (GrpcFixture f = new GrpcFixture(true, List.of())) {
            f.service.handler = r -> {
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
            };
            GrpcPermissionProvider failing = new GrpcPermissionProvider(f.channel);
            assertThatThrownBy(() -> failing.permissionsOf(USER)).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
            down = new GrpcPermissionProvider(f.channel);
        }
        // fixture đã đóng: kênh chết, vẫn phải từ chối chứ không trả tập rỗng
        assertThatThrownBy(() -> down.permissionsOf(USER)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    // ------------------------------------------------------------ danh mục quyền

    private static final class FakeCatalog extends PermissionCatalogServiceGrpc.PermissionCatalogServiceImplBase {
        volatile RegisterRequest seen;
        volatile RuntimeException fail;

        @Override
        public void register(RegisterRequest request, StreamObserver<RegisterResponse> out) {
            seen = request;
            if (fail != null) {
                throw fail;
            }
            out.onNext(RegisterResponse.newBuilder().setCreated(1).setUpdated(2).setUnchanged(3)
                    .addRejected(RejectedCode.newBuilder().setCode("billing:x").setReason("OWNED_BY_OTHER")).build());
            out.onCompleted();
        }
    }

    @Test
    void publisherMapsRequestAndResponse() throws Exception {
        String name = "catalog-" + UUID.randomUUID();
        FakeCatalog fake = new FakeCatalog();
        Server server = InProcessServerBuilder.forName(name).directExecutor().addService(io.grpc.ServerInterceptors.intercept(fake, new GrpcExceptionInterceptor())).build().start();
        var channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            PublishResult result = new GrpcPermissionCatalogPublisher(channel).publish(new PermissionCatalog(
                    "order-service", "1.2.3", null,
                    List.of(new PermissionDef("order:read", "Read orders"), new PermissionDef("order:cancel", ""))));

            assertThat(fake.seen.getServiceName()).isEqualTo("order-service");
            assertThat(fake.seen.getServiceVersion()).isEqualTo("1.2.3");
            assertThat(fake.seen.getInstanceId()).isEmpty();
            assertThat(fake.seen.getPermissionsList()).extracting("code")
                    .containsExactly("order:read", "order:cancel");
            assertThat(fake.seen.getPermissions(0).getDescription()).isEqualTo("Read orders");

            assertThat(result.created()).isEqualTo(1);
            assertThat(result.updated()).isEqualTo(2);
            assertThat(result.unchanged()).isEqualTo(3);
            assertThat(result.rejected()).containsExactly(
                    new com.htv.shared.auth.PermissionCatalogPublisher.RejectedCode("billing:x", "OWNED_BY_OTHER"));

            fake.fail = new BusinessException(CommonErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> new GrpcPermissionCatalogPublisher(channel)
                    .publish(new PermissionCatalog("x", "1", "i", List.of())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode().code()).isEqualTo("COMMON-4030"));
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
