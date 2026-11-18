package com.htv.starter.core;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Đặt {@link RequestContext} vào {@link ContextHolder} và MDC cùng lúc, rồi khôi phục khi đóng.
 * Mọi cổng vào (servlet filter, gRPC interceptor, listener) dùng lớp này để không quên dọn.
 *
 * <pre>
 * try (var scope = MdcContextBinder.open(ctx)) { ... }
 * </pre>
 */
public final class MdcContextBinder {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String USER_ID = "userId";
    public static final String CHANNEL = "channel";

    private MdcContextBinder() {
    }

    /** Phạm vi ngữ cảnh. {@link #close()} không ném ngoại lệ. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static Scope open(RequestContext context) {
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        ContextHolder.Scope holderScope = ContextHolder.open(context);
        apply(context);
        return () -> {
            holderScope.close();
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
        };
    }

    /** Cập nhật ngữ cảnh đang mở (ví dụ có userId sau khi xác thực) mà không đổi phạm vi. */
    public static void update(RequestContext context) {
        ContextHolder.set(context);
        apply(context);
    }

    private static void apply(RequestContext c) {
        put(TRACE_ID, c == null ? null : c.getTraceId());
        put(SPAN_ID, c == null ? null : c.getSpanId());
        put(USER_ID, c == null ? null : c.getUserId());
        put(CHANNEL, c == null ? null : c.getChannel());
    }

    private static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
