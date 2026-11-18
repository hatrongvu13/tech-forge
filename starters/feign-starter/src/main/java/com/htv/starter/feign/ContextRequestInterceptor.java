package com.htv.starter.feign;

import com.htv.api.model.HeaderConstant;
import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Chép ngữ cảnh vào lời gọi Feign.
 *
 * <ul>
 *   <li>Mọi client: {@code X-Trace-Id}, {@code X-Channel}.
 *   <li>Chỉ client nội bộ ({@code htv.feign.trusted-clients}): thêm {@code Authorization} của request đang xử lý
 *       (service kia tự kiểm JWT) và {@code X-User-Id}.
 * </ul>
 * Header do lập trình viên tự đặt trên client không bị ghi đè.
 */
public class ContextRequestInterceptor implements RequestInterceptor {

    private static final boolean SERVLET_PRESENT =
            ClassUtils.isPresent("jakarta.servlet.http.HttpServletRequest", ContextRequestInterceptor.class.getClassLoader());

    private final Set<String> trustedClients;

    public ContextRequestInterceptor(Set<String> trustedClients) {
        this.trustedClients = Set.copyOf(trustedClients);
    }

    @Override
    public void apply(RequestTemplate template) {
        RequestContext context = ContextHolder.current().orElse(null);
        if (context != null) {
            putIfAbsent(template, HeaderConstant.TRACE_ID, context.getTraceId());
            putIfAbsent(template, HeaderConstant.CHANNEL, context.getChannel());
        }
        if (!isTrusted(template)) {
            return;
        }
        if (context != null) {
            putIfAbsent(template, HeaderConstant.USER_ID, context.getUserId());
        }
        putIfAbsent(template, HttpHeaders.AUTHORIZATION, currentAuthorization());
    }

    private boolean isTrusted(RequestTemplate template) {
        return template.feignTarget() != null && trustedClients.contains(template.feignTarget().name());
    }

    private static String currentAuthorization() {
        if (!SERVLET_PRESENT) {
            return null;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            String value = request.getHeader(HttpHeaders.AUTHORIZATION);
            return value != null && value.startsWith("Bearer ") ? value : null;
        }
        return null;
    }

    private static void putIfAbsent(RequestTemplate template, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        boolean present = template.headers().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
        if (!present) {
            template.header(name, value);
        }
    }
}
