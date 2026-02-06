package com.example.coreclash;

import android.app.AlertDialog;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.coreclash.view.VictoryLineView;

import java.util.List;
import java.util.Random;

import game.GameState;
import manager.BoardManager;
import manager.GameManager;

public class MainActivity extends AppCompatActivity {

    private enum SelectedMode { CASUAL, RANKED }
    private enum BotDifficulty {
        INICIANTE("Iniciante"),
        MODERADA("Moderada"),
        MESTRE("Mestre do Jogo");

        final String label;
        BotDifficulty(String label) { this.label = label; }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private GameManager gameManager;
    private GameState state;
    private BoardManager board;

    private FrameLayout btnTriangle;
    private FrameLayout btnSquare;
    private FrameLayout victoryOverlay;
    private FrameLayout homeOverlay;
    private FrameLayout versusOverlay;
    private View victoryCard;
    private TextView txtStatus;
    private TextView txtWinnerTitle;
    private TextView txtStatsMoves;
    private TextView txtStatsGhosts;
    private TextView txtVersusX;
    private TextView txtVersusO;
    private TextView txtVersusCenter;
    private VictoryLineView victoryLineView;

    private Button btnRestart;
    private Button btnExit;
    private Button btnPlay;
    private Button btnOnline;
    private Button btnStore;
    private Button btnSettings;

    private boolean matchStarted = false;
    private boolean versusBot = false;
    private SelectedMode selectedMode = SelectedMode.CASUAL;
    private BotDifficulty currentBotDifficulty = BotDifficulty.INICIANTE;
    private String opponentName = "Adversário";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hideSystemBars();
        setContentView(R.layout.activity_main);

        initUI();

        state = new GameState();
        board = new BoardManager();
        gameManager = new GameManager(board, state);

        GridLayout gridBoard = findViewById(R.id.gridBoard);
        board.createBoard(this, gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) {
                return;
            }

            if (versusBot && !state.isXTurn()) {
                return;
            }

            playTurn(row, col);
        });

        setupMetaControls();
        setupSkills();
        setupHomeFlow();

        updateHeaderStatus();
        updateSkillVisuals();
    }

    private void initUI() {
        txtStatus = findViewById(R.id.txtStatus);
        btnTriangle = findViewById(R.id.containerTriangle);
        btnSquare = findViewById(R.id.containerSquare);
        victoryOverlay = findViewById(R.id.victoryOverlay);
        homeOverlay = findViewById(R.id.homeOverlay);
        versusOverlay = findViewById(R.id.versusOverlay);
        victoryCard = findViewById(R.id.victoryCard);
        txtWinnerTitle = findViewById(R.id.txtWinnerTitle);
        txtStatsMoves = findViewById(R.id.txtStatsMoves);
        txtStatsGhosts = findViewById(R.id.txtStatsGhosts);
        txtVersusX = findViewById(R.id.txtVersusX);
        txtVersusO = findViewById(R.id.txtVersusO);
        txtVersusCenter = findViewById(R.id.txtVersusCenter);
        victoryLineView = findViewById(R.id.victoryLineView);

        btnRestart = findViewById(R.id.btnRestart);
        btnExit = findViewById(R.id.btnExit);
        btnPlay = findViewById(R.id.btnPlay);
        btnOnline = findViewById(R.id.btnOnline);
        btnStore = findViewById(R.id.btnStore);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupHomeFlow() {
        btnStore.setOnClickListener(v -> Toast.makeText(this, "Loja em desenvolvimento 🚧", Toast.LENGTH_SHORT).show());
        btnSettings.setOnClickListener(v -> Toast.makeText(this, "Configurações em desenvolvimento ⚙", Toast.LENGTH_SHORT).show());
        btnOnline.setOnClickListener(v -> Toast.makeText(this, "Fila online ativa (beta)", Toast.LENGTH_SHORT).show());

        btnPlay.setOnClickListener(v -> openModeSelectionModal());

        showHomeScreen();
    }

    private void openModeSelectionModal() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mode_selection, null);
        RadioGroup radioGroup = dialogView.findViewById(R.id.radioMode);
        RadioButton radioCasual = dialogView.findViewById(R.id.radioCasual);
        RadioButton radioRanked = dialogView.findViewById(R.id.radioRanked);

        if (selectedMode == SelectedMode.CASUAL) {
            radioCasual.setChecked(true);
        } else {
            radioRanked.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Buscar partida", (d, which) -> {
                    selectedMode = radioGroup.getCheckedRadioButtonId() == R.id.radioRanked
                            ? SelectedMode.RANKED : SelectedMode.CASUAL;
                    startMatchmaking();
                })
                .create();

        dialog.show();
    }

    private void startMatchmaking() {
        boolean foundPlayer = random.nextFloat() < 0.35f;
        versusBot = !foundPlayer;

        if (versusBot) {
            opponentName = randomBotName();
            currentBotDifficulty = randomDifficulty();
            Toast.makeText(this, "Sem jogadores disponíveis. Bot " + opponentName + " (" + currentBotDifficulty.label + ") entrou na partida!", Toast.LENGTH_LONG).show();
        } else {
            opponentName = "RivalOnline" + (100 + random.nextInt(900));
            currentBotDifficulty = BotDifficulty.MODERADA;
            Toast.makeText(this, "Partida online encontrada contra " + opponentName, Toast.LENGTH_SHORT).show();
        }

        startMatchIntro();
    }

    private void showHomeScreen() {
        matchStarted = false;
        txtStatus.setText("CORE CLASH | MENU");

        homeOverlay.setVisibility(View.VISIBLE);
        homeOverlay.setAlpha(1f);
        versusOverlay.setVisibility(View.GONE);
    }

    private void startMatchIntro() {
        homeOverlay.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    homeOverlay.setVisibility(View.GONE);
                    showVersusOverlay();
                })
                .start();
    }

    private void showVersusOverlay() {
        versusOverlay.setVisibility(View.VISIBLE);
        versusOverlay.setAlpha(0f);

        txtVersusX.setText("Você");
        txtVersusCenter.setText(selectedMode == SelectedMode.RANKED ? "RANQUEADA" : "CASUAL");
        txtVersusO.setText(opponentName + (versusBot ? " 🤖" : ""));

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

        handler.postDelayed(() -> {
            versusOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        versusOverlay.setVisibility(View.GONE);
                        matchStarted = true;
                        state.setGameMode(selectedMode == SelectedMode.CASUAL ? GameState.GameMode.CASUAL : GameState.GameMode.RANKED);
                        updateHeaderStatus();
                    })
                    .start();
        }, 1500);
    }

    private void setupMetaControls() {
        btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            victoryLineView.clear();
            updateHeaderStatus();
            updateSkillVisuals();
            startMatchIntro();
        });

        btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            victoryLineView.clear();
            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void setupSkills() {
        btnTriangle.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver()) {
                shakeButton(v);
                return;
            }

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
            if (!matchStarted || gameManager.isGameOver()) {
                shakeButton(v);
                return;
            }

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

    private void playTurn(int row, int col) {
        int beforeMoves = gameManager.getFinalMoves();
        String currentSymbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            onWin(currentSymbol);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            Toast.makeText(this, "Empate!", Toast.LENGTH_SHORT).show();
            return;
        }

        maybeRunBotTurn();
    }

    private void onWin(String symbol) {
        matchStarted = false;
        drawVictoryLine();
        handler.postDelayed(() -> showVictoryScreen(symbol), 500);
    }

    private void maybeRunBotTurn() {
        if (!versusBot || !matchStarted || gameManager.isGameOver() || state.isXTurn()) {
            return;
        }

        handler.postDelayed(() -> {
            if (!matchStarted || gameManager.isGameOver() || state.isXTurn()) {
                return;
            }

            if (currentBotDifficulty == BotDifficulty.MESTRE) {
                if (state.canUseTriangle() && random.nextFloat() < 0.35f) {
                    gameManager.useTriangle();
                }
                if (state.canUseSquare() && random.nextFloat() < 0.35f) {
                    gameManager.useSquare();
                }
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) {
                playTurn(move[0], move[1]);
            }

            updateHeaderStatus();
            updateSkillVisuals();
        }, 550);
    }

    private int[] chooseBotMove(BotDifficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (moves.isEmpty()) {
            return null;
        }

        if (difficulty == BotDifficulty.INICIANTE) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] winning = gameManager.findWinningMoveFor("O");
        if (winning != null) return winning;

        int[] block = gameManager.findWinningMoveFor("X");
        if (block != null) return block;

        if (difficulty == BotDifficulty.MODERADA) {
            int[] center = gameManager.getCenterIfAvailable();
            if (center != null) return center;
            return moves.get(random.nextInt(moves.size()));
        }

        int[] best = gameManager.findBestMoveForO();
        return best != null ? best : moves.get(random.nextInt(moves.size()));
    }

    private void updateHeaderStatus() {
        txtStatus.setText(String.format(
                "CORE CLASH | %s | %s",
                selectedMode == SelectedMode.CASUAL ? "CASUAL" : "RANQUEADA",
                versusBot ? (opponentName + " • " + currentBotDifficulty.label) : opponentName
        ));
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

    private void drawVictoryLine() {
        GameManager.WinInfo win = gameManager.getLastWin();
        if (win == null) return;

        PointF start = board.getCellCenterOnScreen(win.r1(), win.c1());
        PointF end = board.getCellCenterOnScreen(win.r3(), win.c3());

        int[] lineLoc = new int[2];
        victoryLineView.getLocationOnScreen(lineLoc);

        victoryLineView.setData(
                start.x - lineLoc[0],
                start.y - lineLoc[1],
                end.x - lineLoc[0],
                end.y - lineLoc[1]
        );
    }

    private String randomBotName() {
        String[] names = {
                "GuaxinimDaNoite", "NinjaDoVazio", "DrakeDeAço", "CorujaSuprema",
                "TigreNebuloso", "FalcaoTatico", "LinceArcano", "RaptorDigital"
        };
        return names[random.nextInt(names.length)];
    }

    private BotDifficulty randomDifficulty() {
        BotDifficulty[] values = BotDifficulty.values();
        return values[random.nextInt(values.length)];
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

    @Override
    protected void onResume() {
        super.onResume();
        maybeRunBotTurn();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        maybeRunBotTurn();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(this::maybeRunBotTurn, 120);
    }

    @Override
    protected void onUserInteraction() {
        super.onUserInteraction();
        if (versusBot && matchStarted && !state.isXTurn()) {
            maybeRunBotTurn();
        }
    }
}
