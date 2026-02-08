package com.example.coreclash;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.coreclash.data.FirebaseProfileRepository;
import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;
import view.CoreBoardView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import enums.BotNames;
import enums.Difficulty;
import enums.GameMode;
import game.GameState;
import manager.AuthenticationManager;
import manager.BoardManager;
import manager.GameManager;
import manager.ProfileManager;
import manager.SettingManager;
import manager.StoreManager;
import util.AnimationHelper;

public class MainActivity extends AppCompatActivity {

    public final Handler handler = new Handler(Looper.getMainLooper());
    private static final Random random = new Random();

    private ActivityMainBinding binding;
    private GameManager gameManager;
    private GameState state;
    private BoardManager board;
    private ProfileManager profileManager;
    private StoreManager storeManager;
    private AuthenticationManager authenticationManager;
    private SettingManager settingManager;

    private PlayerProfile currentProfile;

    private boolean matchStarted = false;
    private boolean versusBot = false;
    private String opponentName = "Aguardando";
    private GameMode selectedMode = GameMode.CASUAL;
    private Difficulty currentBotDifficulty = Difficulty.INICIANTE;
    private static final int MATCH_DURATION_SECONDS = 120;
    private int remainingMatchSeconds = MATCH_DURATION_SECONDS;
    private boolean timeExpired = false;

    private final Runnable matchTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!matchStarted || gameManager.isGameOver()) return;

            remainingMatchSeconds--;
            updateTurnTimerStatus();

            if (remainingMatchSeconds <= 0) {
                timeExpired = true;
                matchStarted = false;
                showDrawScreen();
                return;
            }

            handler.postDelayed(this, 1000);
        }
    };

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authenticationManager = new AuthenticationManager(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        hideSystemBars();
        setupGoogleSignInLauncher();

        state = new GameState();
        board = new BoardManager();
        gameManager = new GameManager(board, state);
        settingManager = new SettingManager(this, binding);

        setupBoardInteraction();

        setupSkills();
        setupHomeFlow();
        initPlayerServices();
        setupMetaControls();

        showHomeScreen();
        updateModeButtonStyles();
        updateHeaderStatus();
        updateSkillVisuals();
    }

    private void setupBoardInteraction() {
        binding.gameBoardView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!matchStarted || gameManager.isGameOver()) return true;
                if (versusBot && !state.isXTurn()) return true;

                float cellW = v.getWidth() / 3f;
                float cellH = v.getHeight() / 3f;
                int col = (int) (event.getX() / cellW);
                int row = (int) (event.getY() / cellH);

                if (row >= 0 && row < 3 && col >= 0 && col < 3) {
                    playTurn(row, col);
                }
            }
            return true;
        });
    }

    private void playTurn(int row, int col) {
        if (!gameManager.canPlayAt(row, col)) {
            AnimationHelper.shakeView(binding.gameBoardView);
            return;
        }

        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        binding.gameBoardView.updateBoard(board.getMatrix());

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            stopMatchTimer();
            drawVictoryLine();
            handler.postDelayed(() -> showVictoryScreen(symbol), 700);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            stopMatchTimer();
            handler.postDelayed(this::showDrawScreen, 420);
            return;
        }

        maybeRunBotTurn();
    }

    private void drawVictoryLine() {
        GameManager.WinInfo win = gameManager.getLastWin();
        if (win == null) return;

        binding.victoryLineView.setData(win.r1(), win.c1(), win.r3(), win.c3());
        binding.victoryLineView.startVictoryAnimation();

        AnimationHelper.shakeView(binding.gameBoardView);
    }

    private void resetGameUI() {
        gameManager.resetGame();
        binding.victoryLineView.clear();
        binding.gameBoardView.updateBoard(board.getMatrix());
        resetMatchTimer();
        updateSkillVisuals();
    }

    private void setupSkills() {
        binding.containerTriangle.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseTriangle()) {
                AnimationHelper.shakeView(v);
                return;
            }
            gameManager.useTriangle();
            binding.gameBoardView.updateBoard(board.getMatrix());
            AnimationHelper.spin(v);
            updateHeaderStatus();
            updateSkillVisuals();
        });

        binding.containerSquare.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseSquare()) {
                AnimationHelper.shakeView(v);
                return;
            }
            gameManager.useSquare();
            binding.gameBoardView.updateBoard(board.getMatrix());
            AnimationHelper.pulse(v);
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void setupMetaControls() {
        binding.btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            resetGameUI();
            startMatchIntro();
        });
        binding.btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            resetGameUI();
            showHomeScreen();
            updateHeaderStatus();
        });
    }

    private void setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        authenticationManager.handleSignInResult(result.getData(), new AuthenticationManager.AuthCallback() {
                            @Override
                            public void onGoogleLinked(String displayName) {
                                if (currentProfile != null) {
                                    currentProfile.displayName = displayName;
                                    profileManager.persistProfile();
                                }
                                updateHeaderStatus();
                                Toast.makeText(MainActivity.this, getString(R.string.toast_progress_linked), Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onFailure(String message) {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }

    private void setupHomeFlow() {
        binding.btnPlay.setOnClickListener(v -> openModeModal());
        binding.btnOnline.setOnClickListener(v -> startMatchmaking(true));
        binding.btnStore.setOnClickListener(v -> storeManager.openStore());
        binding.btnSettings.setOnClickListener(v -> settingManager.openSettings());
        binding.btnModeCasual.setOnClickListener(v -> { selectedMode = GameMode.CASUAL; updateModeButtonStyles(); });
        binding.btnModeRanked.setOnClickListener(v -> { selectedMode = GameMode.RANKED; updateModeButtonStyles(); });
        binding.btnModeCancel.setOnClickListener(v -> closeModeModal());
        binding.btnModeConfirm.setOnClickListener(v -> { closeModeModal(); startMatchmaking(false); });
        binding.modeOverlay.setOnClickListener(v -> closeModeModal());
        binding.btnGoogleLoginSettings.setOnClickListener(v -> { settingManager.closeSettings(); startGoogleSignIn(); });
    }

    private void startGoogleSignIn() {
        authenticationManager.startGoogleSignIn(googleSignInLauncher);
    }

    private void openModeModal() {
        updateModeButtonStyles();
        binding.modeOverlay.setVisibility(View.VISIBLE);
        binding.modeOverlay.setAlpha(0f);
        binding.modeCard.setScaleX(0.9f);
        binding.modeCard.setScaleY(0.9f);

        binding.modeOverlay.animate().alpha(1f).setDuration(180).start();
        binding.modeCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    private void closeModeModal() {
        binding.modeOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> binding.modeOverlay.setVisibility(View.GONE))
                .start();
    }

    private void updateModeButtonStyles() {
        boolean casual = selectedMode == GameMode.CASUAL;
        int selectedBg = 0xFF22D3EE;
        int selectedText = 0xFF082F49;
        int defaultBg = 0xFF312E81;
        int defaultText = 0xFFE0E7FF;

        binding.btnModeCasual.setBackgroundTintList(ColorStateList.valueOf(casual ? selectedBg : defaultBg));
        binding.btnModeCasual.setTextColor(casual ? selectedText : defaultText);

        binding.btnModeRanked.setBackgroundTintList(ColorStateList.valueOf(casual ? defaultBg : selectedBg));
        binding.btnModeRanked.setTextColor(casual ? defaultText : selectedText);
    }

    private void startMatchmaking(boolean fromOnlineButton) {
        boolean foundPlayer = fromOnlineButton && random.nextFloat() < 0.45f;
        versusBot = !foundPlayer;
        opponentName = versusBot ? randomBotName() : getString(R.string.online_rival_prefix) + (100 + random.nextInt(900));
        currentBotDifficulty = versusBot ? randomDifficulty() : Difficulty.MODERADA;

        state.setGameMode(selectedMode == GameMode.RANKED ? GameState.GameMode.RANKED : GameState.GameMode.CASUAL);
        resetGameUI();
        updateHeaderStatus();
        startMatchIntro();
    }

    private void showHomeScreen() {
        matchStarted = false;
        stopMatchTimer();
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);
        updateTurnTimerStatus();
    }

    private void startMatchIntro() {
        binding.homeOverlay.animate().alpha(0f).setDuration(460).withEndAction(() -> {
            binding.homeOverlay.setVisibility(View.GONE);
            showVersusOverlay();
        }).start();
    }

    private void showVersusOverlay() {
        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(0f);
        binding.versusOverlay.animate().alpha(1f).setDuration(160).withEndAction(() -> {
            handler.postDelayed(() -> {
                binding.versusOverlay.animate().alpha(0f).setDuration(240).withEndAction(() -> {
                    binding.versusOverlay.setVisibility(View.GONE);
                    matchStarted = true;
                    startMatchTimer();
                    maybeRunBotTurn();
                }).start();
            }, 1000);
        }).start();
    }

    private void animateVictoryCard() {
        binding.victoryOverlay.setVisibility(View.VISIBLE);
        binding.victoryOverlay.setAlpha(0f);
        binding.victoryCard.setTranslationY(300f);
        binding.victoryOverlay.animate().alpha(1f).setDuration(280).start();
        binding.victoryCard.animate().translationY(0f).setDuration(520).setInterpolator(new OvershootInterpolator(1f)).start();
    }

    private String getPlayerDisplayName() {
        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }

        if (currentProfile != null && currentProfile.displayName != null && !currentProfile.displayName.isEmpty()) {
            return currentProfile.displayName;
        }
        return getString(R.string.default_player_name);
    }

    private void maybeRunBotTurn() {
        if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
            return;
        }

        handler.postDelayed(() -> {
            if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
                return;
            }

            if (currentBotDifficulty == Difficulty.MESTRE) {
                if (state.canUseTriangle() && random.nextFloat() < 0.35f) {
                    gameManager.useTriangle();
                }
                if (state.canUseSquare() && random.nextFloat() < 0.25f) {
                    gameManager.useSquare();
                }
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) {
                playTurn(move[0], move[1]);
            }
        }, 550);
    }

    private int[] chooseBotMove(Difficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (moves.isEmpty()) return null;

        if (difficulty == Difficulty.INICIANTE) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor("O");
        if (win != null) return win;

        int[] block = gameManager.findWinningMoveFor("X");
        if (block != null) return block;

        if (difficulty == Difficulty.MODERADA) {
            int[] center = gameManager.getCenterIfAvailable();
            return center != null ? center : moves.get(random.nextInt(moves.size()));
        }

        int[] best = gameManager.findBestMoveForO();
        return best != null ? best : moves.get(random.nextInt(moves.size()));
    }

    private void showDrawScreen() {
        binding.txtWinnerTitle.setText(timeExpired ? R.string.game_time_up : R.string.game_draw);
        binding.txtStatsMoves.setText(getString(R.string.stats_moves, gameManager.getFinalMoves(), "="));
        binding.txtStatsGhosts.setText(getString(R.string.stats_ghosts, gameManager.getFinalGhosts(), gameManager.getWinStreak()));
        animateVictoryCard();
    }

    private void showVictoryScreen(String winner) {
        binding.txtWinnerTitle.setText(getString(R.string.game_win, winner));
        String winStats = getString(R.string.stats_wins_format, gameManager.getTotalWins());
        binding.txtStatsMoves.setText(getString(R.string.stats_moves, gameManager.getFinalMoves(), winStats));
        binding.txtStatsGhosts.setText(getString(R.string.stats_ghosts, gameManager.getFinalGhosts(), gameManager.getWinStreak()));
        animateVictoryCard();
    }

    private void hideVictoryScreen() {
        binding.victoryOverlay.animate().alpha(0f).setDuration(240).withEndAction(() -> binding.victoryOverlay.setVisibility(View.GONE)).start();
    }

    public void updateHeaderStatus() {
        if (currentProfile == null) {
            binding.txtStatus.setText(R.string.status_sync);
            updateTurnTimerStatus();
            return;
        }
        binding.txtStatus.setText(getString(R.string.versus_status, getPlayerDisplayName(), opponentName));
        updateTurnTimerStatus();
    }

    private void updateTurnTimerStatus() {
        if (!matchStarted) {
            binding.txtTurnTimer.setText(R.string.match_turn_waiting);
            return;
        }

        int turnLabelRes = state.isXTurn() ? R.string.match_turn_you : R.string.match_turn_opponent;
        binding.txtTurnTimer.setText(getString(turnLabelRes, formatRemainingTime()));
    }

    private String formatRemainingTime() {
        int safeSeconds = Math.max(remainingMatchSeconds, 0);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void resetMatchTimer() {
        stopMatchTimer();
        timeExpired = false;
        remainingMatchSeconds = MATCH_DURATION_SECONDS;
        updateTurnTimerStatus();
    }

    private void startMatchTimer() {
        stopMatchTimer();
        handler.postDelayed(matchTimerRunnable, 1000);
        updateTurnTimerStatus();
    }

    private void stopMatchTimer() {
        handler.removeCallbacks(matchTimerRunnable);
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() && matchStarted ? 1f : 0.25f;
        float sqAlpha = state.canUseSquare() && matchStarted ? 1f : 0.25f;
        binding.containerTriangle.animate().alpha(triAlpha).setDuration(220).start();
        binding.containerSquare.animate().alpha(sqAlpha).setDuration(220).start();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    public static String randomBotName() {
        BotNames[] values = BotNames.values();
        return values[random.nextInt(values.length)].getDisplayName();
    }

    private Difficulty randomDifficulty() {
        Difficulty[] levels = Difficulty.values();
        return levels[random.nextInt(levels.length)];
    }

    private void initPlayerServices() {
        profileManager = new ProfileManager();
        profileManager.localProfileRepository = new LocalProfileRepository(this);

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.signInAnonymously().addOnSuccessListener(authResult -> {
            String firebaseUid = Objects.requireNonNull(authResult.getUser()).getUid();
            profileManager.profileRepository = new FirebaseProfileRepository();
            profileManager.profileRepository.loadOrCreateProfile(new ProfileRepository.Callback() {
                @Override
                public void onSuccess(@NonNull PlayerProfile profile) {
                    profile.uid = firebaseUid;
                    onProfileReady(profile);
                }
                @Override
                public void onError(@NonNull String error) { loadLocalFallback(); }
            });
        }).addOnFailureListener(e -> loadLocalFallback());
    }

    private void onProfileReady(PlayerProfile profile) {
        this.currentProfile = profile;
        profileManager.setCurrentProfile(profile);
        profileManager.localProfileRepository.saveProfile(profile);

        storeManager = new StoreManager(this, binding, profile, profileManager, board);

        runOnUiThread(() -> {
            storeManager.applyEquippedCosmetics();
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void loadLocalFallback() {
        var localRepo = new LocalProfileRepository(this);

        localRepo.loadOrCreateProfile(new ProfileRepository.Callback() {
            @Override
            public void onSuccess(@NonNull PlayerProfile profile) {
                currentProfile = profile;
                runOnUiThread(() -> {
                    updateHeaderStatus();
                    updateSkillVisuals();
                    Toast.makeText(MainActivity.this, getString(R.string.toast_offline_loaded), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(@NonNull String error) {
                Log.e("Fallback", "Critical error");
                currentProfile = PlayerProfile.createDefault("temp_" + System.currentTimeMillis());
                updateHeaderStatus();
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopMatchTimer();
        super.onDestroy();
    }
}
