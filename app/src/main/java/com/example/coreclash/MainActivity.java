package com.example.coreclash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import game.GameState;
import manager.BoardManager;
import manager.GameManager;

public class MainActivity extends AppCompatActivity {

    private GameManager gameManager;
    private GameState state;

    private FrameLayout btnTriangle, btnSquare, victoryOverlay;
    private View victoryCard;
    private TextView txtHeaderStatus, txtWinnerTitle, txtStatsMoves, txtStatsGhosts;
    private Button btnRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemBars();
        setContentView(R.layout.activity_main);

        initUI();

        state = new GameState();
        BoardManager board = new BoardManager();
        gameManager = new GameManager(board, state);

        GridLayout gridBoard = findViewById(R.id.gridBoard);

        board.createBoard(this, gridBoard, (row, col) -> {
            String currentSymbol = gameManager.getCurrentPlayerSymbol();

            if (gameManager.play(row, col)) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> showVictoryScreen(currentSymbol), 2000);
            }
            updateHeaderStatus();
            updateSkillVisuals();
        });

        setupMetaControls();
        setupSkills();

        btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            updateHeaderStatus();
            updateSkillVisuals();
        });

        updateHeaderStatus();
        updateSkillVisuals();
    }

    private void initUI() {
        txtHeaderStatus = findViewById(R.id.txtStatus);
        btnTriangle = findViewById(R.id.containerTriangle);
        btnSquare = findViewById(R.id.containerSquare);
        victoryOverlay = findViewById(R.id.victoryOverlay);
        victoryCard = findViewById(R.id.victoryCard);
        txtWinnerTitle = findViewById(R.id.txtWinnerTitle);
        txtStatsMoves = findViewById(R.id.txtStatsMoves);
        txtStatsGhosts = findViewById(R.id.txtStatsGhosts);
        btnRestart = findViewById(R.id.btnRestart);
    }

    private void setupMetaControls() {
        txtHeaderStatus.setOnClickListener(v -> {
            GameState.GameMode nextMode = gameManager.getGameMode() == GameState.GameMode.CASUAL
                    ? GameState.GameMode.RANKED
                    : GameState.GameMode.CASUAL;
            gameManager.setGameMode(nextMode);
            updateHeaderStatus();
        });

        txtHeaderStatus.setOnLongClickListener(v -> {
            GameState.SymbolSkin current = gameManager.getSymbolSkin();
            GameState.SymbolSkin next;
            if (current == GameState.SymbolSkin.CLASSIC) {
                next = GameState.SymbolSkin.NEON;
            } else if (current == GameState.SymbolSkin.NEON) {
                next = GameState.SymbolSkin.GLITCH;
            } else {
                next = GameState.SymbolSkin.CLASSIC;
            }
            gameManager.setSymbolSkin(next);
            updateHeaderStatus();
            return true;
        });
    }

    private void setupSkills() {
        btnTriangle.setOnClickListener(v -> {
            if (state.canUseTriangle()) {
                gameManager.useTriangle();
                spinAnimation(v);
                updateHeaderStatus();
                updateSkillVisuals();
            } else {
                shakeButton(v);
            }
        });

        btnSquare.setOnClickListener(v -> {
            if (state.canUseSquare()) {
                gameManager.useSquare();
                pulseAnimation(v);
                updateHeaderStatus();
                updateSkillVisuals();
            } else {
                shakeButton(v);
            }
        });
    }

    private void updateHeaderStatus() {
        txtHeaderStatus.setText(String.format(
                "CORE CLASH | %s | %s | %s %d",
                gameManager.getGameMode().name(),
                gameManager.getSymbolSkin().name(),
                gameManager.getRankLabel(),
                gameManager.getRankedPoints()
        ));
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() ? 1.0f : 0.2f;
        float sqAlpha = state.canUseSquare() ? 1.0f : 0.2f;

        btnTriangle.animate().alpha(triAlpha).setDuration(300).start();
        btnSquare.animate().alpha(sqAlpha).setDuration(300).start();

        btnTriangle.setElevation(state.canUseTriangle() ? 20f : 0f);
        btnSquare.setElevation(state.canUseSquare() ? 20f : 0f);

        if (!state.canUseSquare() && gameManager.getFinalMoves() < gameManager.getSquareUnlockMove()) {
            btnSquare.setContentDescription("Quadrado bloqueado até a jogada " + gameManager.getSquareUnlockMove());
        } else {
            btnSquare.setContentDescription("Habilidade quadrado disponível");
        }
    }

    private void showVictoryScreen(String winner) {
        txtWinnerTitle.setText("'" + winner + "' DOMINOU");
        txtStatsMoves.setText("⚡ MOVIMENTOS: " + gameManager.getFinalMoves() +
                " | 🏆 WINS: " + gameManager.getTotalWins());
        txtStatsGhosts.setText("👻 FANTASMAS: " + String.format("%02d", gameManager.getFinalGhosts()) +
                " | 🔥 STREAK: " + gameManager.getWinStreak());

        victoryOverlay.setVisibility(View.VISIBLE);
        victoryOverlay.setAlpha(0f);
        victoryCard.setTranslationY(400f);

        victoryOverlay.animate().alpha(1f).setDuration(400).start();
        victoryCard.animate()
                .translationY(0f)
                .setInterpolator(new OvershootInterpolator(1.0f))
                .setDuration(700)
                .start();
    }

    private void hideVictoryScreen() {
        victoryOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> victoryOverlay.setVisibility(View.GONE))
                .start();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void pulseAnimation(View v) {
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction(() ->
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200)
                        .setInterpolator(new android.view.animation.BounceInterpolator()));
    }

    private void spinAnimation(View v) {
        v.animate().rotationBy(360).setDuration(500).setInterpolator(new OvershootInterpolator());
    }

    private void shakeButton(View v) {
        v.animate().translationX(15).setDuration(50).withEndAction(() ->
                v.animate().translationX(-15).setDuration(50).withEndAction(() ->
                        v.animate().translationX(0).setDuration(50)));
    }
}
