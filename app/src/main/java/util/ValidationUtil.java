package util;

public final class ValidationUtil {

    private ValidationUtil() {
    }

    public static boolean isValidUid(String uid) {
        return StringUtil.hasText(uid) && uid.trim().length() >= 4;
    }

    public static boolean isValidRoomCode(String roomCode) {
        return StringUtil.hasText(roomCode) && roomCode.trim().length() >= 4;
    }

    public static boolean isValidTag(String tag) {
        return StringUtil.hasText(tag) && tag.trim().contains("#");
    }
}
