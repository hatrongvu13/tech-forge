package com.htv.starter.web;

import com.htv.api.model.HeaderConstant;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Đọc userId từ header {@code X-User-Id}. Chỉ dùng khi tin được nguồn gửi header (xem {@link WebProperties}).
 */
class HeaderUserIdResolver implements UserIdResolver {

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        return Optional.ofNullable(TraceIds.safe(request.getHeader(HeaderConstant.USER_ID)));
    }
}
