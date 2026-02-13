package util;

public final class StringUtil {

    private StringUtil() {
    }

    public static boolean isBlank(String value) {
        return NullUtil.isNull(value) || value.trim().isEmpty();
    }

    public static boolean hasText(String value) {
        return !isBlank(value);
    }

    public static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    public static String trimOrEmpty(String value) {
        return NullUtil.isNull(value) ? "" : value.trim();
    }

    public static String safePrefixUpper(String value, int length) {
        if (isBlank(value)) return "";
        String normalized = value.trim();
        return normalized.substring(0, Math.min(Math.max(0, length), normalized.length())).toUpperCase();
    }
}
