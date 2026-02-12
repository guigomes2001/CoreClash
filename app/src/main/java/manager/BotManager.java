package manager;

import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Random;

import enums.DomainDifficulty;
import enums.DomainSymbols;

public class BotManager {

    public interface Gate {
        boolean isOnlineMatch();
        boolean isVersusBot();
        boolean isMatchStarted();
        boolean isXTurn();
        boolean isGameOver();
        @NonNull DomainDifficulty getDifficulty();
    }

    public interface Callbacks {
        void onRender();
        void onBotPlayMove(int r, int c);
    }

    private final Handler handler;
    private final Random random;
    private final GameManager gameManager;
    private final Gate gate;
    private final Callbacks cb;

    private Runnable scheduledBotRunnable;

    public BotManager(@NonNull Handler handler,
                      @NonNull Random random,
                      @NonNull GameManager gameManager,
                      @NonNull Gate gate,
                      @NonNull Callbacks callbacks) {
        this.handler = handler;
        this.random = random;
        this.gameManager = gameManager;
        this.gate = gate;
        this.cb = callbacks;
    }

    public void maybeRunBotTurn() {
        if (!canBotActNow()) return;

        cancelPending();

        long thinkDelayMs = 900L + random.nextInt(700);
        scheduledBotRunnable = () -> {
            if (!canBotActNow()) return;

            DomainDifficulty difficulty = gate.getDifficulty();

            if (shouldBotUseSkill(difficulty) && tryUseRandomBotSkill()) {
                cb.onRender();
                return;
            }

            int[] move = chooseBotMove(difficulty);
            if (move != null) cb.onBotPlayMove(move[0], move[1]);
        };

        handler.postDelayed(scheduledBotRunnable, thinkDelayMs);
    }

    public void cancelPending() {
        if (scheduledBotRunnable != null) {
            handler.removeCallbacks(scheduledBotRunnable);
            scheduledBotRunnable = null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean canBotActNow() {
        if (gate.isOnlineMatch() || !gate.isVersusBot() || !gate.isMatchStarted() || gate.isGameOver()) {
            return false;
        }
        return !gate.isXTurn();
    }

    private boolean shouldBotUseSkill(@NonNull DomainDifficulty difficulty) {
        double chance = switch (difficulty) {
            case BEGINNER -> 0.20;
            case MODERATE -> 0.55;
            case GAME_MASTER -> 0.80;
        };
        return random.nextDouble() < chance;
    }

    private boolean tryUseRandomBotSkill() {
        boolean canTriangle = gameManager.canUseTriangleNow();
        boolean canSquare = gameManager.canUseSquareNow();
        if (!canTriangle && !canSquare) return false;

        if (canTriangle && canSquare) {
            return random.nextBoolean() ? gameManager.useTriangle() : gameManager.useSquare();
        }
        return canTriangle ? gameManager.useTriangle() : gameManager.useSquare();
    }

    private int[] chooseBotMove(@NonNull DomainDifficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (moves.isEmpty()) return null;

        if (difficulty == DomainDifficulty.BEGINNER) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor(DomainSymbols.O.getValue());
        if (win != null) return win;

        int[] block = gameManager.findWinningMoveFor(DomainSymbols.X.getValue());
        if (block != null) return block;

        if (difficulty == DomainDifficulty.MODERATE) {
            int[] center = gameManager.getCenterIfAvailable();
            return center != null ? center : moves.get(random.nextInt(moves.size()));
        }

        int[] best = gameManager.findBestMoveForO();
        return best != null ? best : moves.get(random.nextInt(moves.size()));
    }
}
