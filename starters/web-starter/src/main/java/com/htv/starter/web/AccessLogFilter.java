package com.htv.starter.web;

import com.htv.shared.common.ContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mỗi request một dòng: method, đường dẫn, status, thời gian, userId, traceId.
 * Không bao giờ ghi query string, body, hay header (kể cả Authorization).
 */
public class AccessLogFilter extends OncePerRequestFilter {

    static final String LOGGER_NAME = "htv.access";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final List<String> excludePaths;

    public AccessLogFilter(List<String> excludePaths) {
        this.excludePaths = List.copyOf(excludePaths);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        for (String pattern : excludePaths) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        int status = 500;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } catch (Throwable t) {
            status = 500;
            throw t;
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.info("{} {} -> {} {}ms userId={} traceId={}", request.getMethod(), path(request), status, ms,
                    ContextHolder.userId().orElse("-"), ContextHolder.traceId().orElse("-"));
        }
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }
}
