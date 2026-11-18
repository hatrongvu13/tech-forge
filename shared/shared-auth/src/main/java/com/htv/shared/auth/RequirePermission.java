package com.htv.shared.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Yêu cầu người dùng hiện tại có quyền {@link #value()}. Đặt trên method, hoặc trên class
 * làm mặc định cho mọi method (method ghi đè class).
 *
 * <p>Mã quyền dạng {@code <miền>:<hành động>}, ví dụ {@code order:read}. Mã là bất biến: đổi tên
 * là thay đổi phá vỡ (xem {@link PermissionCodes}). Các mã được quét lúc khởi động và đăng ký
 * vào danh mục của identity-service.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {

    /** Mã quyền cụ thể (không dùng ký tự đại diện). */
    String value();

    /** Mô tả hiển thị cho quản trị viên khi gán quyền. */
    String description() default "";
}
