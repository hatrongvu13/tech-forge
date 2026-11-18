package com.htv.starter.security;

import com.htv.shared.common.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401: chưa đăng nhập, hoặc token sai/hết hạn. Không tiết lộ lý do chi tiết cho client.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        ApiErrorWriter.write(response, CommonErrorCode.UNAUTHORIZED);
    }
}
