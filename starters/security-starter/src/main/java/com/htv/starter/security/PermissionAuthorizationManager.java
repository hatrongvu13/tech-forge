package com.htv.starter.security;

import com.htv.shared.auth.PermissionCodes;
import com.htv.shared.auth.PermissionProvider;
import com.htv.shared.auth.RequirePermission;
import com.htv.shared.common.BusinessException;
import com.htv.shared.common.CommonErrorCode;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;

/**
 * Quyết định cho {@link RequirePermission}: lấy quyền của người dùng qua {@link PermissionProvider}
 * rồi so khớp (hỗ trợ {@code *} và {@code <miền>:*}).
 *
 * <p>Từ chối khi: chưa đăng nhập, {@code sub} không phải UUID, thiếu quyền. Khi không xác định được
 * quyền (Redis/identity lỗi), {@link BusinessException} {@code COMMON-5030} được ném ra, tức fail-closed.
 */
@Slf4j
public class PermissionAuthorizationManager implements AuthorizationManager<MethodInvocation> {

    private final ObjectProvider<PermissionProvider> provider;

    public PermissionAuthorizationManager(ObjectProvider<PermissionProvider> provider) {
        this.provider = provider;
    }

    @Override
    public AuthorizationResult authorize(@NonNull Supplier<? extends Authentication> authentication,
                                         MethodInvocation invocation) {
        RequirePermission required = RequirePermissions.resolve(invocation.getMethod(),
                invocation.getThis() == null ? null : invocation.getThis().getClass());
        if (required == null) {
            return new AuthorizationDecision(true);
        }
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return new AuthorizationDecision(false);
        }
        UUID userId = parse(auth.getName());
        if (userId == null) {
            log.warn("Token subject is not a UUID, denying {}", required.value());
            return new AuthorizationDecision(false);
        }
        PermissionProvider permissions = provider.getIfAvailable();
        if (permissions == null) {
            // Đã bị chặn lúc khởi động bởi RequirePermissionScanner; giữ lại để an toàn.
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "No PermissionProvider configured");
        }
        Set<String> granted = permissions.permissionsOf(userId);
        boolean allowed = PermissionCodes.anyCovers(granted, required.value());
        if (!allowed) {
            log.info("Permission denied: user={} required={}", userId, required.value());
        }
        return new AuthorizationDecision(allowed);
    }

    private static UUID parse(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
