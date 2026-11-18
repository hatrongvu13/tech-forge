package com.htv.starter.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Xác định userId của request cho {@code ContextHolder}. security-starter (JWT) cung cấp bản riêng.
 * Bản mặc định đọc header {@code X-User-Id} và CHỈ bật khi {@code htv.web.trust-user-header=true}.
 */
@FunctionalInterface
public interface UserIdResolver {

    Optional<String> resolve(HttpServletRequest request);
}
