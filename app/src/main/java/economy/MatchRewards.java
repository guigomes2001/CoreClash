package economy;

public final class MatchRewards {

    public enum Outcome {
        WIN,
        DRAW,
        LOSS
    }

    private static final int WIN_BASE = 25;
    private static final int DRAW_BASE = 8;
    private static final int LOSS_BASE = 3;
    private static final int SPEED_REFERENCE_MOVES = 9;
    private static final int STREAK_STEP = 5;
    private static final int STREAK_BONUS_CAP = 25;

    private MatchRewards() {
    }

    public static int coinsFor(Outcome outcome, boolean ranked, int winStreakAfterMatch, int moveCount) {
        int coins = switch (outcome) {
            case WIN -> WIN_BASE + speedBonus(moveCount) + streakBonus(winStreakAfterMatch);
            case DRAW -> DRAW_BASE;
            case LOSS -> LOSS_BASE;
        };

        if (ranked) {
            coins = (coins * 3) / 2;
        }
        return coins;
    }

    private static int speedBonus(int moveCount) {
        return Math.max(0, SPEED_REFERENCE_MOVES - moveCount);
    }

    private static int streakBonus(int winStreakAfterMatch) {
        int chainedWins = Math.max(0, winStreakAfterMatch - 1);
        return Math.min(STREAK_BONUS_CAP, chainedWins * STREAK_STEP);
    }
}
