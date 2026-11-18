package com.htv.starter.security;

import com.htv.shared.common.ContextHolder;
import com.htv.shared.common.RequestContext;
import com.htv.starter.core.ContextValues;
import com.htv.starter.core.MdcContextBinder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chạy SAU bước xác thực JWT: đưa {@code sub} của token vào {@link RequestContext} (ContextHolder + MDC),
 * để log, gRPC và Feign phía sau thấy userId. Nếu chưa có ngữ cảnh (không có web-starter) thì tự mở phạm vi
 * và dọn khi xong.
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = currentUser();
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }
        RequestContext current = ContextHolder.current().orElse(null);
        if (current != null) {
            // phạm vi do filter ở ngoài mở và dọn
            MdcContextBinder.update(current.toBuilder().userId(userId).build());
            chain.doFilter(request, response);
            return;
        }
        RequestContext fresh = RequestContext.builder().traceId(ContextValues.newTraceId()).userId(userId).build();
        try (MdcContextBinder.Scope ignored = MdcContextBinder.open(fresh)) {
            chain.doFilter(request, response);
        }
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return ContextValues.safe(auth.getName());
    }
}
