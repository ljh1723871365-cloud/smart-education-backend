package com.ljh.smarteducation.util;
import java.util.regex.Pattern;
public class InputValidator {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,50}$");
    private static final Pattern TITLE_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_,.，。！？]{1,200}$");
    private static final Pattern SQL_KEYWORDS_PATTERN = Pattern.compile(
        "\\b(SELECT|INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|EXEC|EXECUTE|UNION|TRUNCATE|GRANT|REVOKE|MERGE|CALL|DECLARE)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SQL_COMMENTS_PATTERN = Pattern.compile("--|/\\*|\\*/|#");
    private static final Pattern SQL_INJECTION_PATTERNS = Pattern.compile(
        "'\\s*OR\\s*'1'\\s*=\\s*'1|'\\s*OR\\s*'1'\\s*=\\s*'1'--|'\\s*OR\\s*'1'\\s*=\\s*'1'/\\*|';\\s*DROP\\s+TABLE|'\\s*UNION\\s+SELECT|'\\s*AND\\s+1\\s*=\\s*1|admin'--|admin'/\\*",
        Pattern.CASE_INSENSITIVE
    );
    private InputValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        return USERNAME_PATTERN.matcher(username).matches();
    }
    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        int length = password.length();
        return length >= 6 && length <= 100;
    }
    public static boolean isValidTitle(String title) {
        if (title == null) return false;
        return TITLE_PATTERN.matcher(title).matches();
    }
    public static boolean isValidNumericId(String id) {
        if (id == null) return false;
        try {
            Long.parseLong(id);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    public static boolean isValidLength(String input, int maxLength) {
        if (input == null) return false;
        return input.length() <= maxLength;
    }
    public static boolean isValidLength(String input, int minLength, int maxLength) {
        if (input == null) return false;
        int length = input.length();
        return length >= minLength && length <= maxLength;
    }
    public static boolean containsSqlInjection(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        String upperInput = input.toUpperCase();
        if (SQL_KEYWORDS_PATTERN.matcher(upperInput).find()) return true;
        if (SQL_COMMENTS_PATTERN.matcher(input).find()) return true;
        if (SQL_INJECTION_PATTERNS.matcher(input).find()) return true;
        return false;
    }
    public static String sanitizeInput(String input) {
        if (input == null) return null;
        String sanitized = input;
        sanitized = sanitized.replace("'", "''");
        sanitized = sanitized.replace(";", "");
        sanitized = sanitized.replace("--", "");
        sanitized = sanitized.replace("/*", "");
        sanitized = sanitized.replace("*/", "");
        sanitized = sanitized.replace("#", "");
        return sanitized;
    }
}