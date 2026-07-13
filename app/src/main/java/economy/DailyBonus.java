package economy;

public final class DailyBonus {

    public record Grant(int streakDay, int coins) { }

    private static final int[] COINS_BY_STREAK_DAY = {40, 55, 70, 85, 100, 120, 150};

    private DailyBonus() {
    }

    public static long epochDayOf(long timestampMs) {
        return timestampMs / 86_400_000L;
    }

    public static Grant evaluate(long lastClaimEpochDay, int currentStreak, long todayEpochDay) {
        if (lastClaimEpochDay >= todayEpochDay) {
            return null;
        }

        boolean keptStreak = todayEpochDay - lastClaimEpochDay == 1;
        int streakDay = keptStreak ? Math.max(1, currentStreak) + 1 : 1;

        int tableIndex = Math.min(streakDay, COINS_BY_STREAK_DAY.length) - 1;
        return new Grant(streakDay, COINS_BY_STREAK_DAY[tableIndex]);
    }
}
