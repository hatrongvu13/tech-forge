package com.htv.starter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.shared.auth.PermissionCatalogPublisher;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ServerInterceptor;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GrpcAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
            .withPropertyValues("htv.grpc.server.port=0");

    private static GrpcFixture.FakeQuery service() {
        return new GrpcFixture.FakeQuery();
    }

    private static ManagedChannel plain(int port) {
        return Grpc.newChannelBuilder("localhost:" + port, InsecureChannelCredentials.create()).build();
    }

    @Test
    void startsServerWithHealthAndHandlesCallsThroughTheChain() {
        GrpcFixture.FakeQuery svc = service();
        svc.handler = r -> java.util.List.of("order:read");
        runner.withBean(GrpcFixture.FakeQuery.class, () -> svc).run(ctx -> {
            GrpcServerLifecycle lifecycle = ctx.getBean(GrpcServerLifecycle.class);
            assertThat(lifecycle.isRunning()).isTrue();
            ManagedChannel ch = plain(lifecycle.getPort());
            try {
                HealthCheckResponse health = HealthGrpc.newBlockingStub(ch).check(HealthCheckRequest.getDefaultInstance());
                assertThat(health.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);

                var resp = PermissionQueryServiceGrpc.newBlockingStub(ch)
                        .getUserPermissions(GetUserPermissionsRequest.newBuilder().setUserId("u").build());
                assertThat(resp.getPermissionsList()).containsExactly("order:read");
                // không mTLS => không tin x-user-id; traceId vẫn được sinh
                assertThat(svc.seenUserId).isNull();
                assertThat(svc.seenTraceId).matches("[0-9a-f]{32}");
            } finally {
                ch.shutdownNow();
            }
        });
    }

    @Test
    void serverStopsWhenContextCloses() {
        AtomicReference<GrpcServerLifecycle> ref = new AtomicReference<>();
        runner.withBean(GrpcFixture.FakeQuery.class, GrpcAutoConfigurationTest::service)
                .run(ctx -> ref.set(ctx.getBean(GrpcServerLifecycle.class)));
        assertThat(ref.get().isRunning()).isFalse();
        assertThat(ref.get().getPort()).isEqualTo(-1);
    }

    @Test
    void customServerInterceptorRunsInsideTheBuiltInChain() {
        AtomicReference<String> userSeenByCustom = new AtomicReference<>();
        ServerInterceptor custom = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                    io.grpc.ServerCall<ReqT, RespT> call, io.grpc.Metadata headers,
                    io.grpc.ServerCallHandler<ReqT, RespT> next) {
                // phải nằm trong phạm vi ngữ cảnh: traceId đã có
                userSeenByCustom.set(ContextHolder.traceId().orElse("NONE"));
                return next.startCall(call, headers);
            }
        };
        runner.withBean(GrpcFixture.FakeQuery.class, GrpcAutoConfigurationTest::service)
                .withBean("custom", ServerInterceptor.class, () -> custom).run(ctx -> {
                    ManagedChannel ch = plain(ctx.getBean(GrpcServerLifecycle.class).getPort());
                    try {
                        PermissionQueryServiceGrpc.newBlockingStub(ch)
                                .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance());
                    } finally {
                        ch.shutdownNow();
                    }
                    assertThat(userSeenByCustom.get()).matches("[0-9a-f]{32}");
                });
    }

    @Test
    void noServerWithoutAnyService() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(GrpcServerLifecycle.class)
                .hasSingleBean(GrpcChannelFactory.class));
    }

    @Test
    void canBeDisabled() {
        runner.withBean(GrpcFixture.FakeQuery.class, GrpcAutoConfigurationTest::service)
                .withPropertyValues("htv.grpc.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GrpcServerLifecycle.class)
                        .doesNotHaveBean(GrpcChannelFactory.class));
    }

    @Test
    void identityBeansExistOnlyWhenAddressConfigured() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(PermissionProvider.class)
                .doesNotHaveBean(PermissionCatalogPublisher.class));
        runner.withPropertyValues("htv.grpc.client.channels.identity.address=localhost:1").run(ctx -> {
            assertThat(ctx).hasBean("grpcPermissionProvider").hasSingleBean(PermissionCatalogPublisher.class);
            assertThat(ctx.getBean(PermissionProvider.class)).isInstanceOf(GrpcPermissionProvider.class);
            assertThat(ctx.getBean(PermissionCatalogPublisher.class)).isInstanceOf(GrpcPermissionCatalogPublisher.class);
        });
    }

    @Test
    void serviceCanOverrideProviderAndPublisher() {
        PermissionProvider mine = id -> java.util.Set.of();
        runner.withPropertyValues("htv.grpc.client.channels.identity.address=localhost:1")
                .withBean("grpcPermissionProvider", PermissionProvider.class, () -> mine)
                .withBean("myPublisher", PermissionCatalogPublisher.class, () -> c -> null)
                .run(ctx -> {
                    assertThat(ctx.getBean(PermissionProvider.class)).isSameAs(mine);
                    assertThat(ctx).hasSingleBean(PermissionCatalogPublisher.class);
                });
    }

    @Test
    void channelFactoryReportsMissingAddress() {
        runner.run(ctx -> {
            GrpcChannelFactory factory = ctx.getBean(GrpcChannelFactory.class);
            assertThat(factory.has("order")).isFalse();
            assertThatThrownBy(() -> factory.channel("order")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("htv.grpc.client.channels.order.address");
        });
        runner.withPropertyValues("htv.grpc.client.channels.order.address=localhost:1").run(ctx -> {
            GrpcChannelFactory factory = ctx.getBean(GrpcChannelFactory.class);
            assertThat(factory.has("order")).isTrue();
            assertThat(factory.channel("order")).isSameAs(factory.channel("order"));
        });
    }

    @Test
    void clientCallsOnConfiguredChannelCarryContextToServer() {
        GrpcFixture.FakeQuery svc = service();
        runner.withBean(GrpcFixture.FakeQuery.class, () -> svc).run(ctx -> {
            int port = ctx.getBean(GrpcServerLifecycle.class).getPort();
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
                    .withPropertyValues("htv.grpc.client.channels.self.address=localhost:" + port).run(client -> {
                        var channel = client.getBean(GrpcChannelFactory.class).channel("self");
                        try (ContextHolder.Scope ignored = ContextHolder.open(
                                RequestContext.builder().traceId("trace-xyz").build())) {
                            PermissionQueryServiceGrpc.newBlockingStub(channel)
                                    .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance());
                        }
                        assertThat(svc.seenTraceId).isEqualTo("trace-xyz");
                    });
        });
    }

    // ------------------------------------------------------------ cấu hình TLS / tin cậy

    @Test
    void trustUserMetadataDefaultsToMtlsOnly() {
        GrpcProperties p = new GrpcProperties();
        assertThat(p.getServer().effectiveTrustUserMetadata()).isFalse();
        p.getServer().getTls().setClientAuth(GrpcProperties.ClientAuth.OPTIONAL);
        assertThat(p.getServer().effectiveTrustUserMetadata()).isFalse();
        p.getServer().getTls().setClientAuth(GrpcProperties.ClientAuth.REQUIRE);
        assertThat(p.getServer().effectiveTrustUserMetadata()).isTrue();
        p.getServer().setTrustUserMetadata(false);
        assertThat(p.getServer().effectiveTrustUserMetadata()).isFalse();
    }

    @Test
    void tlsMisconfigurationFailsFast() {
        GrpcProperties.Tls onlyAuth = new GrpcProperties.Tls();
        onlyAuth.setClientAuth(GrpcProperties.ClientAuth.REQUIRE);
        assertThatThrownBy(() -> GrpcTls.server(onlyAuth)).isInstanceOf(IllegalStateException.class);

        GrpcProperties.Tls noKey = new GrpcProperties.Tls();
        noKey.setCertChain("/nope/cert.pem");
        assertThatThrownBy(() -> GrpcTls.server(noKey)).hasMessageContaining("private-key");

        GrpcProperties.Tls mtlsNoCa = new GrpcProperties.Tls();
        mtlsNoCa.setCertChain("/nope/cert.pem");
        mtlsNoCa.setPrivateKey("/nope/key.pem");
        mtlsNoCa.setClientAuth(GrpcProperties.ClientAuth.REQUIRE);
        assertThatThrownBy(() -> GrpcTls.server(mtlsNoCa)).hasMessageContaining("trust-certs");

        GrpcProperties.Tls unreadable = new GrpcProperties.Tls();
        unreadable.setCertChain("/nope/cert.pem");
        unreadable.setPrivateKey("/nope/key.pem");
        assertThatThrownBy(() -> GrpcTls.server(unreadable)).hasMessageContaining("Cannot read");

        GrpcProperties.Tls clientHalf = new GrpcProperties.Tls();
        clientHalf.setCertChain("/nope/cert.pem");
        assertThatThrownBy(() -> GrpcTls.client("identity", clientHalf)).hasMessageContaining("private-key");
        assertThat(GrpcTls.client("identity", new GrpcProperties.Tls())).isNotNull();
        assertThat(GrpcTls.server(new GrpcProperties.Tls())).isNotNull();
    }
}
