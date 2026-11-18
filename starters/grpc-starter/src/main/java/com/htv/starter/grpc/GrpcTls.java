package com.htv.starter.grpc;

import io.grpc.ChannelCredentials;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import java.io.File;
import java.io.IOException;

/** Dựng thông tin TLS/mTLS từ file PEM. Cấu hình thiếu hoặc sai thì báo lỗi ngay lúc khởi động. */
final class GrpcTls {

    private GrpcTls() {
    }

    static ServerCredentials server(GrpcProperties.Tls tls) {
        if (!tls.isConfigured()) {
            if (tls.getClientAuth() != GrpcProperties.ClientAuth.NONE) {
                throw new IllegalStateException("htv.grpc.server.tls.client-auth needs cert-chain and private-key");
            }
            return InsecureServerCredentials.create();
        }
        require(tls.getCertChain(), "htv.grpc.server.tls.cert-chain");
        require(tls.getPrivateKey(), "htv.grpc.server.tls.private-key");
        if (tls.getClientAuth() != GrpcProperties.ClientAuth.NONE) {
            require(tls.getTrustCerts(), "htv.grpc.server.tls.trust-certs (needed to verify client certificates)");
        }
        try {
            TlsServerCredentials.Builder b = TlsServerCredentials.newBuilder()
                    .keyManager(new File(tls.getCertChain()), new File(tls.getPrivateKey()));
            if (tls.getTrustCerts() != null) {
                b.trustManager(new File(tls.getTrustCerts()));
            }
            b.clientAuth(switch (tls.getClientAuth()) {
                case NONE -> TlsServerCredentials.ClientAuth.NONE;
                case OPTIONAL -> TlsServerCredentials.ClientAuth.OPTIONAL;
                case REQUIRE -> TlsServerCredentials.ClientAuth.REQUIRE;
            });
            return b.build();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read gRPC server TLS files: " + e.getMessage(), e);
        }
    }

    static ChannelCredentials client(String channelName, GrpcProperties.Tls tls) {
        if (!tls.isConfigured()) {
            return InsecureChannelCredentials.create();
        }
        try {
            TlsChannelCredentials.Builder b = TlsChannelCredentials.newBuilder();
            if (tls.getCertChain() != null || tls.getPrivateKey() != null) {
                require(tls.getCertChain(), "htv.grpc.client.channels." + channelName + ".tls.cert-chain");
                require(tls.getPrivateKey(), "htv.grpc.client.channels." + channelName + ".tls.private-key");
                b.keyManager(new File(tls.getCertChain()), new File(tls.getPrivateKey()));
            }
            if (tls.getTrustCerts() != null) {
                b.trustManager(new File(tls.getTrustCerts()));
            }
            return b.build();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read gRPC client TLS files for '" + channelName + "': "
                    + e.getMessage(), e);
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
    }
}
