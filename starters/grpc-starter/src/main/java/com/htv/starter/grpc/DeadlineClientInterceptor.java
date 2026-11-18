package com.htv.starter.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Đặt deadline mặc định cho lời gọi chưa có deadline, để một service chậm không treo luồng gọi mãi. */
public class DeadlineClientInterceptor implements ClientInterceptor {

    private final Duration defaultDeadline;

    public DeadlineClientInterceptor(Duration defaultDeadline) {
        this.defaultDeadline = defaultDeadline;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions, Channel next) {
        CallOptions options = callOptions.getDeadline() == null && defaultDeadline != null
                && !defaultDeadline.isZero() && !defaultDeadline.isNegative()
                ? callOptions.withDeadlineAfter(defaultDeadline.toMillis(), TimeUnit.MILLISECONDS)
                : callOptions;
        return next.newCall(method, options);
    }
}
