package manager;

import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import enums.DomainDifficulty;
import enums.DomainSymbols;
import util.CollectionUtil;
import util.NullUtil;

public class BotManager {

    public interface Gate {
        boolean isOnlineMatch();
        boolean isVersusBot();
        boolean isMatchStarted();
        boolean isXTurn();
        boolean isGameOver();
        boolean isActionLocked();
        @NonNull DomainDifficulty getDifficulty();
        boolean isTutorialActive();
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

        long thinkDelayMs = 1400L + random.nextInt(1200);
        scheduledBotRunnable = () -> {
            if (!canBotActNow()) return;

            DomainDifficulty difficulty = gate.getDifficulty();

            if (!gate.isTutorialActive() && shouldBotUseSkill(difficulty) && tryUseRandomBotSkill()) {
                cb.onRender();
                return;
            }

            int[] move = chooseBotMove(difficulty);
            if (!NullUtil.isNull(move)) cb.onBotPlayMove(move[0], move[1]);
        };

        handler.postDelayed(scheduledBotRunnable, thinkDelayMs);
    }

    public void cancelPending() {
        if (!NullUtil.isNull(scheduledBotRunnable)) {
            handler.removeCallbacks(scheduledBotRunnable);
            scheduledBotRunnable = null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean canBotActNow() {
        if (gate.isOnlineMatch() || !gate.isVersusBot() || !gate.isMatchStarted() || gate.isGameOver() || gate.isActionLocked()) {
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

    private int[] chooseTutorialFriendlyMove(@NonNull List<int[]> moves) {
        int[] winning = gameManager.findWinningMoveFor(DomainSymbols.O.getValue());
        if (NullUtil.isNull(winning)) {
            return moves.get(random.nextInt(moves.size()));
        }

        List<int[]> safeMoves = new ArrayList<>();
        for (int[] move : moves) {
            if (move[0] == winning[0] && move[1] == winning[1]) continue;
            safeMoves.add(move);
        }
        if (!safeMoves.isEmpty()) {
            return safeMoves.get(random.nextInt(safeMoves.size()));
        }
        return moves.get(random.nextInt(moves.size()));
    }

    private int[] chooseBotMove(@NonNull DomainDifficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (CollectionUtil.isNullOrEmpty(moves)) return null;

        if (gate.isTutorialActive()) {
            return chooseTutorialFriendlyMove(moves);
        }

        if (difficulty == DomainDifficulty.BEGINNER) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor(DomainSymbols.O.getValue());
        if (!NullUtil.isNull(win)) return win;

        int[] block = gameManager.findWinningMoveFor(DomainSymbols.X.getValue());
        if (!NullUtil.isNull(block)) return block;

        if (difficulty == DomainDifficulty.MODERATE) {
            int[] center = gameManager.getCenterIfAvailable();
            return !NullUtil.isNull(center) ? center : moves.get(random.nextInt(moves.size()));
        }

        int[] best = gameManager.findBestMoveForO();
        return !NullUtil.isNull(best) ? best : moves.get(random.nextInt(moves.size()));
    }
}
