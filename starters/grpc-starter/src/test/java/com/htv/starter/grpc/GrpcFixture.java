package com.htv.starter.grpc;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.GetUserPermissionsResponse;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.shared.common.ContextHolder;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Server/kênh trong tiến trình dùng chung cho test của grpc-starter. */
final class GrpcFixture implements AutoCloseable {

    /** Dịch vụ giả: handler do test quyết định, ghi lại ngữ cảnh nhìn thấy trong handler. */
    static final class FakeQuery extends PermissionQueryServiceGrpc.PermissionQueryServiceImplBase {

        volatile Function<GetUserPermissionsRequest, List<String>> handler = r -> List.of();
        volatile String seenUserId;
        volatile String seenTraceId;
        volatile String seenChannel;

        @Override
        public void getUserPermissions(GetUserPermissionsRequest request,
                                       StreamObserver<GetUserPermissionsResponse> out) {
            seenUserId = ContextHolder.userId().orElse(null);
            seenTraceId = ContextHolder.traceId().orElse(null);
            seenChannel = ContextHolder.current().map(c -> c.getChannel()).orElse(null);
            List<String> perms = handler.apply(request);
            out.onNext(GetUserPermissionsResponse.newBuilder().addAllPermissions(perms).build());
            out.onCompleted();
        }
    }

    final FakeQuery service = new FakeQuery();
    final ManagedChannel channel;
    private final Server server;

    GrpcFixture(boolean trustUser, List<String> logExclude) throws Exception {
        this(trustUser, logExclude, List.of());
    }

    GrpcFixture(boolean trustUser, List<String> logExclude, List<ServerInterceptor> extra) throws Exception {
        String name = "test-" + UUID.randomUUID();
        List<ServerInterceptor> chain = new ArrayList<>();
        chain.add(new ContextServerInterceptor(trustUser));
        chain.add(new GrpcAccessLogInterceptor(logExclude));
        chain.add(new GrpcExceptionInterceptor());
        chain.addAll(extra);
        Collections.reverse(chain); // phần tử cuối chạy trước
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, chain)).build().start();

        List<ClientInterceptor> client = new ArrayList<>();
        client.add(new ContextClientInterceptor());
        client.add(new DeadlineClientInterceptor(Duration.ofSeconds(5)));
        Collections.reverse(client);
        channel = InProcessChannelBuilder.forName(name).directExecutor().intercept(client).build();
    }

    @Override
    public void close() throws Exception {
        channel.shutdownNow();
        server.shutdownNow();
        channel.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        server.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
    }
}
