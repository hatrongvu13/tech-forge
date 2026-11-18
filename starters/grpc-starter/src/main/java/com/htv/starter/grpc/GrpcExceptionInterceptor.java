package com.htv.starter.grpc;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bắt exception ném ra từ handler và đóng lời gọi bằng status chuẩn (xem {@link GrpcErrors}).
 * Lỗi lạ trả {@code INTERNAL "Internal error"}, chi tiết chỉ vào log.
 *
 * <p>Giới hạn: chỉ bắt được exception ném trong callback đồng bộ của handler. Lỗi ở luồng tự tạo
 * phải được handler báo qua {@code responseObserver.onError}.
 */
public class GrpcExceptionInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcExceptionInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        ClosableCall<ReqT, RespT> guarded = new ClosableCall<>(call);
        ServerCall.Listener<ReqT> delegate;
        try {
            delegate = next.startCall(guarded, headers);
        } catch (RuntimeException e) {
            guarded.fail(e);
            return new ServerCall.Listener<>() { };
        }
        return new SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                try {
                    super.onMessage(message);
                } catch (RuntimeException e) {
                    guarded.fail(e);
                }
            }

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (RuntimeException e) {
                    guarded.fail(e);
                }
            }

            @Override
            public void onReady() {
                try {
                    super.onReady();
                } catch (RuntimeException e) {
                    guarded.fail(e);
                }
            }
        };
    }

    /** Nhớ trạng thái đã đóng để không đóng hai lần. */
    private static final class ClosableCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {

        private boolean closed;

        ClosableCall(ServerCall<ReqT, RespT> delegate) {
            super(delegate);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            closed = true;
            super.close(status, trailers);
        }

        void fail(Throwable t) {
            GrpcErrors.Failure f = GrpcErrors.toFailure(t, e -> LOG.error("Unexpected gRPC error", e));
            if (!closed) {
                close(f.status(), f.trailers());
            }
        }
    }
}
