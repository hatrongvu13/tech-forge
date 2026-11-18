package com.htv.starter.security;

import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcut;

/**
 * Khớp method có {@code @RequirePermission} (trên method hoặc trên class). Bỏ qua method của
 * {@code Object} để {@code toString/hashCode} không bị chặn.
 */
class RequirePermissionPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.getDeclaringClass() != Object.class && RequirePermissions.resolve(method, targetClass) != null;
    }
}
