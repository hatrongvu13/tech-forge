package com.htv.starter.grpc;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/** Chép ngữ cảnh hiện tại (traceId, userId, channel) vào metadata của lời gọi đi. */
public class ContextClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                ContextHolder.current().ifPresent(c -> apply(c, headers));
                super.start(responseListener, headers);
            }
        };
    }

    private static void apply(RequestContext c, Metadata headers) {
        put(headers, GrpcMetadataKeys.TRACE_ID, c.getTraceId());
        put(headers, GrpcMetadataKeys.USER_ID, c.getUserId());
        put(headers, GrpcMetadataKeys.CHANNEL, c.getChannel());
    }

    private static void put(Metadata headers, Metadata.Key<String> key, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(key, value);
        }
    }
}
