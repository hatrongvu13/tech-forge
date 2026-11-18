package com.htv.starter.grpc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Thuộc tính {@code htv.grpc.*}. */
@ConfigurationProperties(prefix = "htv.grpc")
public class GrpcProperties {

    /** Tắt toàn bộ grpc-starter. */
    private boolean enabled = true;

    private final Server server = new Server();
    private final Client client = new Client();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Server getServer() {
        return server;
    }

    public Client getClient() {
        return client;
    }

    public enum ClientAuth { NONE, OPTIONAL, REQUIRE }

    /** Cấu hình TLS (PEM). Bỏ trống toàn bộ = không mã hóa (chỉ dùng khi dev). */
    public static class Tls {

        /** Đường dẫn file chuỗi chứng chỉ (PEM). */
        private String certChain;
        /** Đường dẫn file khóa riêng (PEM, PKCS#8). */
        private String privateKey;
        /** Đường dẫn file chứng chỉ CA tin cậy (PEM). */
        private String trustCerts;
        /** Chỉ cho server: có đòi chứng chỉ client không. {@code REQUIRE} = mTLS. */
        private ClientAuth clientAuth = ClientAuth.NONE;

        public boolean isConfigured() {
            return certChain != null || privateKey != null || trustCerts != null;
        }

        public String getCertChain() {
            return certChain;
        }

        public void setCertChain(String certChain) {
            this.certChain = certChain;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getTrustCerts() {
            return trustCerts;
        }

        public void setTrustCerts(String trustCerts) {
            this.trustCerts = trustCerts;
        }

        public ClientAuth getClientAuth() {
            return clientAuth;
        }

        public void setClientAuth(ClientAuth clientAuth) {
            this.clientAuth = clientAuth;
        }
    }

    public static class Server {

        /** Cổng gRPC. {@code 0} = cổng ngẫu nhiên (dùng cho test). */
        private int port = 9090;
        private DataSize maxInboundMessageSize = DataSize.ofMegabytes(4);
        /** Thời gian chờ các lời gọi đang chạy khi tắt. */
        private Duration shutdownTimeout = Duration.ofSeconds(30);
        /** Bật reflection (grpcurl). Tắt mặc định vì lộ danh sách API. */
        private boolean reflection = false;
        /**
         * Tin metadata {@code x-user-id}. Mặc định (để trống): chỉ tin khi {@code tls.client-auth=REQUIRE}
         * (đã xác thực service gọi bằng mTLS).
         */
        private Boolean trustUserMetadata;
        private final Tls tls = new Tls();
        private final AccessLog accessLog = new AccessLog();

        public boolean effectiveTrustUserMetadata() {
            return trustUserMetadata != null ? trustUserMetadata : tls.getClientAuth() == ClientAuth.REQUIRE;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public DataSize getMaxInboundMessageSize() {
            return maxInboundMessageSize;
        }

        public void setMaxInboundMessageSize(DataSize maxInboundMessageSize) {
            this.maxInboundMessageSize = maxInboundMessageSize;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }

        public boolean isReflection() {
            return reflection;
        }

        public void setReflection(boolean reflection) {
            this.reflection = reflection;
        }

        public Boolean getTrustUserMetadata() {
            return trustUserMetadata;
        }

        public void setTrustUserMetadata(Boolean trustUserMetadata) {
            this.trustUserMetadata = trustUserMetadata;
        }

        public Tls getTls() {
            return tls;
        }

        public AccessLog getAccessLog() {
            return accessLog;
        }
    }

    public static class AccessLog {

        private boolean enabled = true;
        /** Phương thức (kiểu Ant, dạng {@code package.Service/Method}) không ghi. */
        private List<String> excludeMethods = new ArrayList<>(List.of("grpc.health.v1.Health/*"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExcludeMethods() {
            return excludeMethods;
        }

        public void setExcludeMethods(List<String> excludeMethods) {
            this.excludeMethods = excludeMethods;
        }
    }

    public static class Client {

        /** Deadline cho lời gọi chưa tự đặt deadline. {@code 0} = không đặt. */
        private Duration defaultDeadline = Duration.ofSeconds(5);
        /** Kênh theo tên. Kênh {@code identity} dùng cho tra quyền và đăng ký danh mục quyền. */
        private Map<String, Target> channels = new LinkedHashMap<>();

        public Duration getDefaultDeadline() {
            return defaultDeadline;
        }

        public void setDefaultDeadline(Duration defaultDeadline) {
            this.defaultDeadline = defaultDeadline;
        }

        public Map<String, Target> getChannels() {
            return channels;
        }

        public void setChannels(Map<String, Target> channels) {
            this.channels = channels;
        }
    }

    public static class Target {

        /** Địa chỉ, ví dụ {@code dns:///identity-service:9090} hoặc {@code localhost:9090}. */
        private String address;
        /** Chính sách cân bằng tải. {@code round_robin} hợp với headless service của Kubernetes. */
        private String loadBalancingPolicy = "round_robin";
        private final Tls tls = new Tls();

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getLoadBalancingPolicy() {
            return loadBalancingPolicy;
        }

        public void setLoadBalancingPolicy(String loadBalancingPolicy) {
            this.loadBalancingPolicy = loadBalancingPolicy;
        }

        public Tls getTls() {
            return tls;
        }
    }
}
