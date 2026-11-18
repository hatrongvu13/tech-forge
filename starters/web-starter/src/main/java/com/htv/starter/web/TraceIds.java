package com.htv.starter.web;

import com.htv.api.model.HeaderConstant;
import com.htv.starter.core.ContextValues;
import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * Xác định trace id/span id của request. Thứ tự: MDC (do Micrometer Tracing đặt, khi có
 * tracing-starter) &gt; header W3C {@code traceparent} &gt; {@code X-Trace-Id} &gt; tự sinh.
 * Giá trị từ client được kiểm ký tự và độ dài để tránh chèn log.
 */
final class TraceIds {

    private static final Pattern TRACEPARENT =
            Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$");

    private TraceIds() {
    }

    static String traceId(HttpServletRequest request) {
        String fromMdc = safe(MDC.get("traceId"));
        if (fromMdc != null) {
            return fromMdc;
        }
        Matcher m = traceparent(request);
        if (m != null) {
            return m.group(1);
        }
        String fromHeader = safe(request.getHeader(HeaderConstant.TRACE_ID));
        return fromHeader != null ? fromHeader : ContextValues.newTraceId();
    }

    static String spanId(HttpServletRequest request) {
        String fromMdc = safe(MDC.get("spanId"));
        if (fromMdc != null) {
            return fromMdc;
        }
        Matcher m = traceparent(request);
        return m != null ? m.group(2) : null;
    }

    static String safe(String value) {
        return ContextValues.safe(value);
    }

    private static Matcher traceparent(HttpServletRequest request) {
        String header = request.getHeader("traceparent");
        if (header == null) {
            return null;
        }
        Matcher m = TRACEPARENT.matcher(header.trim());
        return m.matches() ? m : null;
    }
}
