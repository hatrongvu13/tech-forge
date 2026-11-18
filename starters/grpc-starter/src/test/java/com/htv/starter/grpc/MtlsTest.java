package com.htv.starter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.contract.identity.v1.GetUserPermissionsRequest;
import com.htv.contract.identity.v1.GetUserPermissionsResponse;
import com.htv.contract.identity.v1.PermissionQueryServiceGrpc;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** mTLS thật (PEM do openssl cấp): danh tính service gọi, niềm tin userId, và từ chối client sai chứng chỉ. */
class MtlsTest {

    static TestPki pki;
    static TestPki.Identity server;
    static TestPki.Identity orderClient;
    static TestPki.Identity strangerClient;
    static TestPki otherCa;

    @BeforeAll
    static void generate() throws Exception {
        Assumptions.assumeTrue(TestPki.opensslAvailable(), "openssl is required for mTLS tests");
        pki = TestPki.create("htv-test-ca");
        server = pki.issue("identity-service");
        orderClient = pki.issue("order-service");
        otherCa = TestPki.create("evil-ca");
        strangerClient = otherCa.issue("order-service");
    }

    /** Dịch vụ ghi lại danh tính service gọi và ngữ cảnh nhìn thấy. */
    static final class Probe extends PermissionQueryServiceGrpc.PermissionQueryServiceImplBase {
        final AtomicReference<String> peer = new AtomicReference<>();
        final AtomicReference<String> user = new AtomicReference<>();

        @Override
        public void getUserPermissions(GetUserPermissionsRequest r, StreamObserver<GetUserPermissionsResponse> out) {
            peer.set(GrpcPeers.serviceName().orElse(null));
            user.set(ContextHolder.userId().orElse(null));
            out.onNext(GetUserPermissionsResponse.getDefaultInstance());
            out.onCompleted();
        }
    }

    private ApplicationContextRunner serverRunner(Probe probe, String... extra) {
        String[] base = {
                "htv.grpc.server.port=0",
                "htv.grpc.server.tls.cert-chain=" + server.cert(),
                "htv.grpc.server.tls.private-key=" + server.key(),
                "htv.grpc.server.tls.trust-certs=" + pki.caCert,
                "htv.grpc.server.tls.client-auth=REQUIRE"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
                .withBean(Probe.class, () -> probe).withPropertyValues(all);
    }

    private static ApplicationContextRunner clientRunner(int port, TestPki.Identity identity, java.nio.file.Path trust) {
        java.util.List<String> props = new java.util.ArrayList<>(java.util.List.of(
                "htv.grpc.client.channels.id.address=localhost:" + port,
                "htv.grpc.client.channels.id.tls.trust-certs=" + trust));
        if (identity != null) {
            props.add("htv.grpc.client.channels.id.tls.cert-chain=" + identity.cert());
            props.add("htv.grpc.client.channels.id.tls.private-key=" + identity.key());
        }
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
                .withPropertyValues(props.toArray(String[]::new));
    }

    @Test
    void serverSeesTheCallerServiceNameFromItsCertificateAndTrustsTheUserId() {
        Probe probe = new Probe();
        serverRunner(probe).run(srv -> {
            int port = srv.getBean(GrpcServerLifecycle.class).getPort();
            clientRunner(port, orderClient, pki.caCert).run(cli -> {
                var channel = cli.getBean(GrpcChannelFactory.class).channel("id");
                try (ContextHolder.Scope ignored = ContextHolder.open(
                        RequestContext.builder().traceId("t-1").userId("user-7").build())) {
                    PermissionQueryServiceGrpc.newBlockingStub(channel)
                            .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance());
                }
                assertThat(probe.peer.get()).isEqualTo("order-service");
                assertThat(probe.user.get()).isEqualTo("user-7"); // mTLS bắt buộc nên được tin
            });
        });
    }

    @Test
    void clientWithoutCertificateIsRejectedByTheHandshake() {
        serverRunner(new Probe()).run(srv -> {
            int port = srv.getBean(GrpcServerLifecycle.class).getPort();
            clientRunner(port, null, pki.caCert).run(cli -> {
                var channel = cli.getBean(GrpcChannelFactory.class).channel("id");
                assertThatThrownBy(() -> PermissionQueryServiceGrpc.newBlockingStub(channel)
                        .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance()))
                        .isInstanceOfSatisfying(StatusRuntimeException.class,
                                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
            });
        });
    }

    @Test
    void clientWithCertificateFromAnotherCaIsRejectedEvenWithTheSameCn() {
        Probe probe = new Probe();
        serverRunner(probe).run(srv -> {
            int port = srv.getBean(GrpcServerLifecycle.class).getPort();
            clientRunner(port, strangerClient, pki.caCert).run(cli -> {
                var channel = cli.getBean(GrpcChannelFactory.class).channel("id");
                assertThatThrownBy(() -> PermissionQueryServiceGrpc.newBlockingStub(channel)
                        .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance()))
                        .isInstanceOf(StatusRuntimeException.class);
                assertThat(probe.peer.get()).isNull();
            });
        });
    }

    @Test
    void clientDoesNotTrustAServerSignedByAnotherCa() {
        serverRunner(new Probe()).run(srv -> {
            int port = srv.getBean(GrpcServerLifecycle.class).getPort();
            clientRunner(port, orderClient, otherCa.caCert).run(cli -> {
                var channel = cli.getBean(GrpcChannelFactory.class).channel("id");
                assertThatThrownBy(() -> PermissionQueryServiceGrpc.newBlockingStub(channel)
                        .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance()))
                        .isInstanceOf(StatusRuntimeException.class);
            });
        });
    }

    @Test
    void explicitlyDistrustingUserMetadataIgnoresUserIdEvenWithMtls() {
        Probe probe = new Probe();
        serverRunner(probe, "htv.grpc.server.trust-user-metadata=false").run(srv -> {
            int port = srv.getBean(GrpcServerLifecycle.class).getPort();
            clientRunner(port, orderClient, pki.caCert).run(cli -> {
                var channel = cli.getBean(GrpcChannelFactory.class).channel("id");
                try (ContextHolder.Scope ignored = ContextHolder.open(
                        RequestContext.builder().traceId("t-2").userId("user-8").build())) {
                    PermissionQueryServiceGrpc.newBlockingStub(channel)
                            .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance());
                }
                assertThat(probe.peer.get()).isEqualTo("order-service");
                assertThat(probe.user.get()).isNull();
            });
        });
    }

    @Test
    void plaintextServerHasNoPeerIdentity() {
        Probe probe = new Probe();
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
                .withBean(Probe.class, () -> probe).withPropertyValues("htv.grpc.server.port=0").run(srv -> {
                    int port = srv.getBean(GrpcServerLifecycle.class).getPort();
                    new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(GrpcAutoConfiguration.class))
                            .withPropertyValues("htv.grpc.client.channels.id.address=localhost:" + port).run(cli -> {
                                PermissionQueryServiceGrpc.newBlockingStub(
                                                cli.getBean(GrpcChannelFactory.class).channel("id"))
                                        .getUserPermissions(GetUserPermissionsRequest.getDefaultInstance());
                                assertThat(probe.peer.get()).isNull();
                            });
                });
    }

    @Test
    void commonNameParsing() {
        assertThat(PeerIdentityInterceptor.commonName(new javax.security.auth.x500.X500Principal("CN=order-service,O=Acme")))
                .isEqualTo("order-service");
        assertThat(PeerIdentityInterceptor.commonName(new javax.security.auth.x500.X500Principal("O=Acme"))).isNull();
        assertThat(PeerIdentityInterceptor.commonName((javax.net.ssl.SSLSession) null)).isNull();
    }
}
