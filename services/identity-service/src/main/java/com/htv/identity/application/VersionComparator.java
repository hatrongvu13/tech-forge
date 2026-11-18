package com.htv.identity.application;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * So sánh version của service chỉ để biết một lần đăng ký có CŨ HƠN lần đã ghi hay không (rolling deploy,
 * rollback). Chỉ so phần số đầu chuỗi ({@code 1.2.3} từ {@code 1.2.3-SNAPSHOT}); phần còn lại bị bỏ qua.
 * Version không bắt đầu bằng số thì không so được và KHÔNG bị coi là cũ hơn.
 */
public final class VersionComparator {

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)");

    private VersionComparator() {
    }

    /** {@code true} nếu {@code candidate} chắc chắn cũ hơn {@code stored}. */
    public static boolean isOlder(String candidate, String stored) {
        int[] a = parse(candidate);
        int[] b = parse(stored);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = NUMERIC_PREFIX.matcher(version.trim());
        if (!m.find()) {
            return null;
        }
        String[] parts = m.group(1).split("\\.");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }
}
