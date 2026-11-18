package com.htv.shared.common;

import java.util.Optional;

/**
 * Giữ {@link RequestContext} của luồng hiện tại (ThreadLocal).
 *
 * <p>Quy tắc: nơi đặt ngữ cảnh phải xóa trong {@code finally}. Cách an toàn nhất là dùng
 * {@link #open(RequestContext)} với try-with-resources, vì nó khôi phục giá trị trước đó
 * (an toàn khi lồng nhau).
 */
public final class ContextHolder {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private ContextHolder() {
    }

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Id người dùng hiện tại, nếu có. */
    public static Optional<String> userId() {
        return current().map(RequestContext::getUserId);
    }

    /** Trace id hiện tại, nếu có. */
    public static Optional<String> traceId() {
        return current().map(RequestContext::getTraceId);
    }

    public static void set(RequestContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Đặt ngữ cảnh và trả về phạm vi; đóng phạm vi sẽ khôi phục ngữ cảnh trước đó. */
    public static Scope open(RequestContext context) {
        RequestContext previous = CURRENT.get();
        set(context);
        return () -> set(previous);
    }

    /** Phạm vi ngữ cảnh. {@link #close()} không ném ngoại lệ. */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
