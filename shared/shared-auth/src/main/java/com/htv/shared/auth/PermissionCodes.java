package com.htv.shared.auth;

import java.util.Collection;
import java.util.regex.Pattern;

/** Định dạng và so khớp mã quyền. */
public final class PermissionCodes {

    /** Mọi quyền. Chỉ dùng ở phía được cấp (grant), không được khai báo trong code. */
    public static final String ALL = "*";

    private static final Pattern DECLARED = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");
    private static final Pattern DOMAIN_WILDCARD = Pattern.compile("^[a-z][a-z0-9-]*:\\*$");

    private PermissionCodes() {
    }

    /** Mã hợp lệ để khai báo trong code ({@code order:read}). */
    public static boolean isValidDeclared(String code) {
        return code != null && DECLARED.matcher(code).matches();
    }

    /** Mã hợp lệ để cấp cho role: mã cụ thể, {@code <miền>:*} hoặc {@code *}. */
    public static boolean isValidGrant(String code) {
        return ALL.equals(code) || isValidDeclared(code) || (code != null && DOMAIN_WILDCARD.matcher(code).matches());
    }

    /** Quyền đã cấp {@code granted} có bao trùm quyền yêu cầu {@code required} không. */
    public static boolean covers(String granted, String required) {
        if (granted == null || required == null) {
            return false;
        }
        if (ALL.equals(granted) || granted.equals(required)) {
            return true;
        }
        if (granted.endsWith(":*")) {
            String domain = granted.substring(0, granted.length() - 1); // giữ dấu ':'
            return required.startsWith(domain);
        }
        return false;
    }

    /** Tập quyền đã cấp có bao trùm quyền yêu cầu không. */
    public static boolean anyCovers(Collection<String> granted, String required) {
        if (granted == null) {
            return false;
        }
        for (String g : granted) {
            if (covers(g, required)) {
                return true;
            }
        }
        return false;
    }
}
