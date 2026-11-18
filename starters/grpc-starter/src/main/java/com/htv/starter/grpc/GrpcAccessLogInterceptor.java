package com.htv.starter.grpc;

import com.htv.shared.common.ContextHolder;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

/**
 * Mỗi lời gọi một dòng: phương thức, status, thời gian, userId, traceId. Không ghi nội dung message
 * hay metadata. Phải đặt trong phạm vi {@link ContextServerInterceptor} và bên ngoài
 * {@link GrpcExceptionInterceptor} (để thấy status cuối cùng).
 */
public class GrpcAccessLogInterceptor implements ServerInterceptor {

    static final String LOGGER_NAME = "htv.grpc.access";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final List<String> excludeMethods;

    public GrpcAccessLogInterceptor(List<String> excludeMethods) {
        this.excludeMethods = List.copyOf(excludeMethods);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        for (String pattern : excludeMethods) {
            if (MATCHER.match(pattern, method)) {
                return next.startCall(call, headers);
            }
        }
        long start = System.nanoTime();
        return next.startCall(new SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                LOG.info("{} -> {} {}ms userId={} traceId={}", method, status.getCode(), ms,
                        ContextHolder.userId().orElse("-"), ContextHolder.traceId().orElse("-"));
                super.close(status, trailers);
            }
        }, headers);
    }
}
