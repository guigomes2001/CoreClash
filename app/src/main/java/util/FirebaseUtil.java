package util;

public final class FirebaseUtil {

    private FirebaseUtil() {
    }

    public static String safeErrorMessage(Throwable error, String fallback) {
        if (NullUtil.isNull(error) || StringUtil.isBlank(error.getMessage())) {
            return fallback;
        }
        return error.getMessage().trim();
    }
}
