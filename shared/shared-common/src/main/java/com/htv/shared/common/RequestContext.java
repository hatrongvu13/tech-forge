package com.htv.shared.common;

import lombok.Builder;
import lombok.Getter;

/** Ngữ cảnh của một request. Bất biến. Đặt vào {@link ContextHolder} ở cổng vào của service. */
@Getter
@Builder(toBuilder = true)
public final class RequestContext {

    private final String traceId;
    private final String spanId;
    private final String userId;
    private final String channel;
    /** Thẻ ngôn ngữ BCP 47, ví dụ {@code vi-VN}. */
    private final String locale;
}
