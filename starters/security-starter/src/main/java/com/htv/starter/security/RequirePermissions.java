package com.htv.starter.security;

import com.htv.shared.auth.RequirePermission;

import java.lang.reflect.Method;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * Tìm {@link RequirePermission} áp dụng cho một method: ưu tiên method, rồi tới class.
 */
final class RequirePermissions {

    private RequirePermissions() {
    }

    static RequirePermission resolve(Method method, Class<?> targetClass) {
        Class<?> userClass = targetClass == null ? method.getDeclaringClass() : ClassUtils.getUserClass(targetClass);
        Method specific = AopUtils.getMostSpecificMethod(method, userClass);
        RequirePermission onMethod = AnnotatedElementUtils.findMergedAnnotation(specific, RequirePermission.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(userClass, RequirePermission.class);
    }
}
