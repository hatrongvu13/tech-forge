package com.htv.starter.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Che thông tin nhạy cảm trong chuỗi trước khi ghi log: token Bearer, JWT, cặp {@code password=...},
 * {@code token=...}, {@code secret=...}, {@code api-key=...}, cùng các mẫu do service khai báo
 * ({@code htv.core.log.mask-patterns}).
 *
 * <p>Các starter dùng lớp này ở mọi chỗ ghi giá trị có thể do người dùng hoặc lập trình viên đưa vào (đường dẫn
 * trong access log, message của lỗi nghiệp vụ). Giới hạn: không che được stack trace mà thư viện log tự in.
 */
public final class LogMasker {

    public static final String MASK = "***";

    private static final List<Rule> BUILT_IN = List.of(
            new Rule(Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1" + MASK),
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"), MASK),
            new Rule(Pattern.compile(
                    "(?i)\\b(password|passwd|pwd|secret|token|access[-_]?token|refresh[-_]?token|api[-_]?key|otp)"
                            + "(\"?\\s*[:=]\\s*\"?)[^\\s\"&,;}]+"), "$1$2" + MASK));

    private static volatile List<Rule> custom = List.of();

    private LogMasker() {
    }

    private record Rule(Pattern pattern, String replacement) {
        String apply(String input) {
            return pattern.matcher(input).replaceAll(replacement);
        }
    }

    /** Che {@code value}. {@code null} trả về {@code null}. */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        for (Rule r : BUILT_IN) {
            result = r.apply(result);
        }
        for (Rule r : custom) {
            result = r.apply(result);
        }
        return result;
    }

    /**
     * Đặt các mẫu riêng của service (biểu thức chính quy; phần khớp được thay bằng {@value #MASK}).
     * Mẫu sai cú pháp thì báo lỗi ngay khi khởi động.
     */
    public static void configure(List<String> patterns) {
        List<Rule> rules = new ArrayList<>();
        for (String p : patterns == null ? List.<String>of() : patterns) {
            if (p != null && !p.isBlank()) {
                rules.add(new Rule(Pattern.compile(p), Matcher.quoteReplacement(MASK)));
            }
        }
        custom = List.copyOf(rules);
    }
}
