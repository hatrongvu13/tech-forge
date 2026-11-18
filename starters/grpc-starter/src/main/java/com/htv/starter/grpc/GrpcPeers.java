package com.htv.starter.grpc;

import io.grpc.Context;
import java.util.Optional;

/**
 * Danh tính service đang gọi, lấy từ CN của chứng chỉ client (mTLS). Chỉ có giá trị khi server chạy
 * mTLS ({@code htv.grpc.server.tls.client-auth=REQUIRE} hoặc {@code OPTIONAL} và client có trình chứng chỉ).
 * Đọc trong handler (luồng xử lý lời gọi).
 */
public final class GrpcPeers {

    static final Context.Key<String> SERVICE_NAME = Context.key("htv-peer-service-name");

    private GrpcPeers() {
    }

    /** CN của chứng chỉ client đã xác thực, nếu có. */
    public static Optional<String> serviceName() {
        return Optional.ofNullable(SERVICE_NAME.get());
    }
}
