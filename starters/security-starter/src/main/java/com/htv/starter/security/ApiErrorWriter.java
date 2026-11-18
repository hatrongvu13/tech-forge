package com.htv.starter.security;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghi lỗi 401/403 của tầng bảo mật theo đúng dạng {@code BaseResponse} ({@code traceId, code, message, data}).
 * Tự dựng JSON (giá trị cố định và traceId đã được kiểm ký tự) để không phụ thuộc thư viện JSON.
 */
final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode code) throws IOException {
        String traceId = ContextHolder.traceId().orElse(null);
        String body = "{\"traceId\":" + (traceId == null ? "null" : quote(traceId))
                + ",\"code\":" + quote(code.code())
                + ",\"message\":" + quote(code.message())
                + ",\"data\":null}";
        response.setStatus(code.httpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
