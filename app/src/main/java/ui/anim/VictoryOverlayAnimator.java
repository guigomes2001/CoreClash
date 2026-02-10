package ui.anim;

import android.graphics.PointF;
import android.os.Handler;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import manager.BoardManager;
import manager.GameManager;

public class VictoryOverlayAnimator {

    private final ActivityMainBinding binding;
    private final BoardManager board;
    private final GameManager gameManager;
    private final Handler handler;

    public VictoryOverlayAnimator(@NonNull ActivityMainBinding binding,
                                  @NonNull BoardManager board,
                                  @NonNull GameManager gameManager,
                                  @NonNull Handler handler) {
        this.binding = binding;
        this.board = board;
        this.gameManager = gameManager;
        this.handler = handler;
    }

    public void clearLines() {
        binding.victoryLineView.clear();
    }

    public void showWin(@NonNull String winnerSymbol, long showDelayMs) {
        drawVictoryLine();

        handler.postDelayed(() -> {
            setWinTexts(winnerSymbol);
            animateVictoryCard();
        }, Math.max(0L, showDelayMs));
    }

    public void showDraw(long showDelayMs) {
        drawDrawLine();

        handler.postDelayed(() -> {
            setDrawTexts();
            animateVictoryCard();
        }, Math.max(0L, showDelayMs));
    }

    public void hide() {
        binding.victoryOverlay.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> binding.victoryOverlay.setVisibility(View.GONE))
                .start();
    }

    public void hideInstant() {
        binding.victoryOverlay.animate().cancel();
        binding.victoryOverlay.setAlpha(0f);
        binding.victoryOverlay.setVisibility(View.GONE);
    }

    private void setDrawTexts() {
        binding.txtWinnerTitle.setText(R.string.game_draw);
        binding.txtStatsMoves.setText(binding.getRoot().getContext().getString(
                R.string.stats_moves, gameManager.getFinalMoves(), "="
        ));
        binding.txtStatsGhosts.setText(binding.getRoot().getContext().getString(
                R.string.stats_ghosts, gameManager.getFinalGhosts(), gameManager.getWinStreak()
        ));
    }

    private void setWinTexts(@NonNull String winner) {
        binding.txtWinnerTitle.setText(binding.getRoot().getContext().getString(R.string.game_win, winner));
        String winStats = binding.getRoot().getContext().getString(R.string.stats_wins_format, gameManager.getTotalWins());
        binding.txtStatsMoves.setText(binding.getRoot().getContext().getString(
                R.string.stats_moves, gameManager.getFinalMoves(), winStats
        ));
        binding.txtStatsGhosts.setText(binding.getRoot().getContext().getString(
                R.string.stats_ghosts, gameManager.getFinalGhosts(), gameManager.getWinStreak()
        ));
    }

    private void animateVictoryCard() {
        binding.victoryOverlay.setVisibility(View.VISIBLE);
        binding.victoryOverlay.setAlpha(0f);
        binding.victoryCard.setTranslationY(300f);

        binding.victoryOverlay.animate().alpha(1f).setDuration(280).start();
        binding.victoryCard.animate()
                .translationY(0f)
                .setDuration(520)
                .setInterpolator(new OvershootInterpolator(1f))
                .start();
    }

    private void drawVictoryLine() {
        GameManager.WinInfo win = gameManager.getLastWin();
        if (win == null) return;

        PointF start = board.getCellCenterOnScreen(win.r1(), win.c1());
        PointF end = board.getCellCenterOnScreen(win.r3(), win.c3());

        int[] lineLoc = new int[2];
        binding.victoryLineView.getLocationOnScreen(lineLoc);

        binding.victoryLineView.setData(
                start.x - lineLoc[0],
                start.y - lineLoc[1],
                end.x - lineLoc[0],
                end.y - lineLoc[1]
        );
    }

    private void drawDrawLine() {
        PointF topLeft     = board.getCellCenterOnScreen(0, 0);
        PointF topRight    = board.getCellCenterOnScreen(0, 2);
        PointF bottomLeft  = board.getCellCenterOnScreen(2, 0);
        PointF bottomRight = board.getCellCenterOnScreen(2, 2);

        int[] lineLoc = new int[2];
        binding.victoryLineView.getLocationOnScreen(lineLoc);

        float x1 = topLeft.x - lineLoc[0];
        float y1 = topLeft.y - lineLoc[1];

        float x2 = bottomRight.x - lineLoc[0];
        float y2 = bottomRight.y - lineLoc[1];

        float x3 = topRight.x - lineLoc[0];
        float y3 = topRight.y - lineLoc[1];

        float x4 = bottomLeft.x - lineLoc[0];
        float y4 = bottomLeft.y - lineLoc[1];

        binding.victoryLineView.setDrawData(x1, y1, x2, y2, x3, y3, x4, y4);
    }
}