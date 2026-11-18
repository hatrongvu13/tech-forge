package com.htv.starter.grpc;

import io.grpc.Metadata;

/** Khóa metadata/trailer gRPC (phải viết thường). */
public final class GrpcMetadataKeys {

    public static final Metadata.Key<String> TRACEPARENT =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> TRACE_ID =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> CHANNEL =
            Metadata.Key.of("x-channel", Metadata.ASCII_STRING_MARSHALLER);
    /** Trailer: mã lỗi nghiệp vụ (ví dụ {@code COMMON-4040}) đi xuyên service. */
    public static final Metadata.Key<String> ERROR_CODE =
            Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcMetadataKeys() {
    }
}
