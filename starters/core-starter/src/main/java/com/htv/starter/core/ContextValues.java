package com.htv.starter.core;

import java.util.UUID;
import java.util.regex.Pattern;

/** Kiểm giá trị ngữ cảnh đến từ bên ngoài (header HTTP, metadata gRPC) trước khi tin hoặc ghi log. */
public final class ContextValues {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private ContextValues() {
    }

    /** Trả chính giá trị nếu chỉ gồm {@code [A-Za-z0-9._-]} và dài tối đa 64 ký tự, ngược lại {@code null} (chống chèn log). */
    public static String safe(String value) {
        return value != null && SAFE.matcher(value).matches() ? value : null;
    }

    /** Trace id mới: 32 ký tự hex. */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
