package util;

public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    public static boolean isExpired(long startedAtMs, long durationMs, long nowMs) {
        return nowMs >= startedAtMs + Math.max(0L, durationMs);
    }
}
