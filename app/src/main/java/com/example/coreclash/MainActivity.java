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

    private FrameLayout btnTriangle;
    private FrameLayout btnSquare;
    private FrameLayout victoryOverlay;
    private FrameLayout startOverlay;
    private FrameLayout versusOverlay;
    private View victoryCard;
    private TextView txtStatus;
    private TextView txtWinnerTitle;
    private TextView txtStatsMoves;
    private TextView txtStatsGhosts;
    private TextView txtVersusX;
    private TextView txtVersusO;
    private TextView txtVersusCenter;
    private Button btnRestart;
    private Button btnPlay;

    private boolean matchStarted = false;

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
            if (!matchStarted) {
                return;
            }

            String currentSymbol = gameManager.getCurrentPlayerSymbol();
            if (gameManager.play(row, col)) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> showVictoryScreen(currentSymbol), 2000);
            }
            updateSkillVisuals();
        });

        setupSkills();
        setupPreMatchFlow();

        btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            updateSkillVisuals();
            setupPreMatchFlow();
        });

        updateSkillVisuals();
    }

    private void initUI() {
        txtStatus = findViewById(R.id.txtStatus);
        btnTriangle = findViewById(R.id.containerTriangle);
        btnSquare = findViewById(R.id.containerSquare);
        victoryOverlay = findViewById(R.id.victoryOverlay);
        startOverlay = findViewById(R.id.startOverlay);
        versusOverlay = findViewById(R.id.versusOverlay);
        victoryCard = findViewById(R.id.victoryCard);
        txtWinnerTitle = findViewById(R.id.txtWinnerTitle);
        txtStatsMoves = findViewById(R.id.txtStatsMoves);
        txtStatsGhosts = findViewById(R.id.txtStatsGhosts);
        txtVersusX = findViewById(R.id.txtVersusX);
        txtVersusO = findViewById(R.id.txtVersusO);
        txtVersusCenter = findViewById(R.id.txtVersusCenter);
        btnRestart = findViewById(R.id.btnRestart);
        btnPlay = findViewById(R.id.btnPlay);
    }

    private void setupPreMatchFlow() {
        matchStarted = false;
        txtStatus.setText("CORE CLASH");

        startOverlay.setVisibility(View.VISIBLE);
        startOverlay.setAlpha(1f);
        versusOverlay.setVisibility(View.GONE);

        btnPlay.setOnClickListener(v -> startMatchIntro());
    }

    private void startMatchIntro() {
        startOverlay.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    startOverlay.setVisibility(View.GONE);
                    showVersusOverlay();
                })
                .start();
    }

    private void showVersusOverlay() {
        versusOverlay.setVisibility(View.VISIBLE);
        versusOverlay.setAlpha(0f);

        txtVersusX.setTranslationX(-260f);
        txtVersusO.setTranslationX(260f);
        txtVersusCenter.setScaleX(0.7f);
        txtVersusCenter.setScaleY(0.7f);

        versusOverlay.animate().alpha(1f).setDuration(180).start();
        txtVersusX.animate().translationX(0f).setDuration(500).setInterpolator(new OvershootInterpolator(1.1f)).start();
        txtVersusO.animate().translationX(0f).setDuration(500).setInterpolator(new OvershootInterpolator(1.1f)).start();
        txtVersusCenter.animate().scaleX(1.2f).scaleY(1.2f).setDuration(260)
                .withEndAction(() -> txtVersusCenter.animate().scaleX(1f).scaleY(1f).setDuration(180).start())
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            versusOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        versusOverlay.setVisibility(View.GONE);
                        txtStatus.setText("BATALHA EM CURSO");
                        matchStarted = true;
                    })
                    .start();
        }, 1500);
    }

    private void setupSkills() {
        btnTriangle.setOnClickListener(v -> {
            if (!matchStarted) {
                shakeButton(v);
                return;
            }

            if (state.canUseTriangle()) {
                gameManager.useTriangle();
                spinAnimation(v);
                updateSkillVisuals();
            } else {
                shakeButton(v);
            }
        });

        btnSquare.setOnClickListener(v -> {
            if (!matchStarted) {
                shakeButton(v);
                return;
            }

            if (state.canUseSquare()) {
                gameManager.useSquare();
                pulseAnimation(v);
                updateSkillVisuals();
            } else {
                shakeButton(v);
            }
        });
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() ? 1.0f : 0.2f;
        float sqAlpha = state.canUseSquare() ? 1.0f : 0.2f;

        if (!matchStarted) {
            triAlpha = 0.25f;
            sqAlpha = 0.25f;
        }

        btnTriangle.animate().alpha(triAlpha).setDuration(300).start();
        btnSquare.animate().alpha(sqAlpha).setDuration(300).start();

        btnTriangle.setElevation(state.canUseTriangle() && matchStarted ? 20f : 0f);
        btnSquare.setElevation(state.canUseSquare() && matchStarted ? 20f : 0f);
    }

    private void showVictoryScreen(String winner) {
        txtWinnerTitle.setText("'" + winner + "' DOMINOU");
        txtStatsMoves.setText("⚡ MOVIMENTOS: " + gameManager.getFinalMoves());
        txtStatsGhosts.setText("👻 FANTASMAS: " + String.format("%02d", gameManager.getFinalGhosts()));

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
