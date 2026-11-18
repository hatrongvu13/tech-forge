package com.htv.starter.grpc;

import com.htv.shared.common.RequestContext;
import com.htv.starter.core.ContextValues;
import com.htv.starter.core.MdcContextBinder;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dựng {@link RequestContext} từ metadata và giữ nó trong ContextHolder + MDC suốt mọi callback của lời gọi
 * (gRPC có thể chạy các callback ở luồng khác nhau, nên phải mở phạm vi cho từng callback).
 *
 * <p>{@code x-user-id} chỉ được tin khi {@code trustUser = true} (server dùng mTLS bắt buộc chứng chỉ client).
 * Nếu không, mọi client trong mạng đều giả được người dùng.
 */
public class ContextServerInterceptor implements ServerInterceptor {

    private static final Pattern TRACEPARENT = Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$");

    private final boolean trustUser;

    public ContextServerInterceptor(boolean trustUser) {
        this.trustUser = trustUser;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        RequestContext context = fromMetadata(headers);
        ServerCall.Listener<ReqT> delegate;
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
            delegate = next.startCall(call, headers);
        }
        return new SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
                    super.onMessage(message);
                }
            }

            @Override
            public void onHalfClose() {
                try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
                    super.onHalfClose();
                }
            }

            @Override
            public void onCancel() {
                try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
                    super.onCancel();
                }
            }

            @Override
            public void onComplete() {
                try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
                    super.onComplete();
                }
            }

            @Override
            public void onReady() {
                try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
                    super.onReady();
                }
            }
        };
    }

    RequestContext fromMetadata(Metadata headers) {
        String traceId = null;
        String spanId = null;
        String traceparent = headers.get(GrpcMetadataKeys.TRACEPARENT);
        if (traceparent != null) {
            Matcher m = TRACEPARENT.matcher(traceparent.trim());
            if (m.matches()) {
                traceId = m.group(1);
                spanId = m.group(2);
            }
        }
        if (traceId == null) {
            traceId = ContextValues.safe(headers.get(GrpcMetadataKeys.TRACE_ID));
        }
        if (traceId == null) {
            traceId = ContextValues.newTraceId();
        }
        return RequestContext.builder()
                .traceId(traceId)
                .spanId(spanId)
                .userId(trustUser ? ContextValues.safe(headers.get(GrpcMetadataKeys.USER_ID)) : null)
                .channel(ContextValues.safe(headers.get(GrpcMetadataKeys.CHANNEL)))
                .build();
    }
}
