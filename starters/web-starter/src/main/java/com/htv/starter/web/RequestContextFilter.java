package com.htv.starter.web;

import com.htv.api.model.HeaderConstant;
import com.htv.shared.common.RequestContext;
import com.htv.starter.core.MdcContextBinder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dựng {@link RequestContext} ở cổng vào, đặt vào ContextHolder + MDC, và luôn dọn trong {@code finally}.
 * Trả {@code X-Trace-Id} cho client. Chạy trước Spring Security nên userId chưa có nếu dùng JWT:
 * security-starter cập nhật ngữ cảnh sau khi xác thực bằng {@link MdcContextBinder#update}.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    private final List<UserIdResolver> userIdResolvers;

    public RequestContextFilter(List<UserIdResolver> userIdResolvers) {
        this.userIdResolvers = List.copyOf(userIdResolvers);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceIds.traceId(request);
        RequestContext context = RequestContext.builder()
                .traceId(traceId)
                .spanId(TraceIds.spanId(request))
                .userId(resolveUser(request))
                .channel(TraceIds.safe(request.getHeader(HeaderConstant.CHANNEL)))
                .locale(request.getLocale() == null ? null : request.getLocale().toLanguageTag())
                .build();
        response.setHeader(HeaderConstant.TRACE_ID, traceId);
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(context)) {
            chain.doFilter(request, response);
        }
    }

    private String resolveUser(HttpServletRequest request) {
        for (UserIdResolver resolver : userIdResolvers) {
            var id = resolver.resolve(request);
            if (id.isPresent()) {
                return id.get();
            }
        }
        return null;
    }
}
