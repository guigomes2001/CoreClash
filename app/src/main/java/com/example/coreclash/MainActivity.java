package com.example.coreclash;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
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
    private Boolean lastTurnProgressIsX = null;

    private static final long TURN_PROGRESS_DURATION_MS = 6000L;
    private ObjectAnimator turnAnimatorX;
    private ObjectAnimator turnAnimatorO;
    private String opponentName = "";
    private GameMode selectedMode = GameMode.CASUAL;
    private Difficulty currentBotDifficulty = Difficulty.INICIANTE;

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

        board.createBoard(this, binding.gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) {
                return;
            }
            if (versusBot && !state.isXTurn()) {
                return;
            }
            playTurn(row, col);
        });

        setupSkills();
        setupHomeFlow();
        initPlayerServices();
        setupMetaControls();

        opponentName = getString(R.string.status_waiting);

        showHomeScreen();
        updateModeButtonStyles();
        updateHeaderStatus();
        updateSkillVisuals();
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

        binding.btnModeCasual.setOnClickListener(v -> {
            selectedMode = GameMode.CASUAL;
            updateModeButtonStyles();
        });

        binding.btnModeRanked.setOnClickListener(v -> {
            selectedMode = GameMode.RANKED;
            updateModeButtonStyles();
        });

        binding.btnModeCancel.setOnClickListener(v -> closeModeModal());
        binding.btnModeConfirm.setOnClickListener(v -> {
            closeModeModal();
            startMatchmaking(false);
        });

        binding.modeOverlay.setOnClickListener(v -> closeModeModal());

        binding.btnGoogleLoginSettings.setOnClickListener(v -> {
            settingManager.closeSettings();
            startGoogleSignIn();
        });
    }

    private void startGoogleSignIn() {
        authenticationManager.startGoogleSignIn(googleSignInLauncher);
    }

    private void setupSkills() {
        binding.containerTriangle.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseTriangle()) {
                AnimationHelper.shakeButton(v);
                return;
            }
            if (gameManager.useTriangle()) {
                AnimationHelper.spin(v);
                updateHeaderStatus();
                updateSkillVisuals();
                maybeRunBotTurn();
            }
        });

        binding.containerSquare.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseSquare()) {
                AnimationHelper.shakeButton(v);
                return;
            }
            if (gameManager.useSquare()) {
                AnimationHelper.pulse(v);
                updateHeaderStatus();
                updateSkillVisuals();
                maybeRunBotTurn();
            }
        });
    }

    private void setupMetaControls() {
        binding.btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            binding.victoryLineView.clear();
            updateSkillVisuals();
            startMatchIntro();
        });

        binding.btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            binding.victoryLineView.clear();
            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });
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

        if (versusBot) {
            opponentName = randomBotName();
            currentBotDifficulty = randomDifficulty();
        } else {
            opponentName = getString(R.string.online_rival_prefix) + (100 + random.nextInt(900));
            currentBotDifficulty = Difficulty.MODERADA;

            String matchMsg = getString(R.string.toast_match_found, opponentName);
            Toast.makeText(this, matchMsg, Toast.LENGTH_SHORT).show();
        }

        state.setGameMode(selectedMode == GameMode.RANKED ? GameState.GameMode.RANKED : GameState.GameMode.CASUAL);
        gameManager.resetGame();
        binding.victoryLineView.clear();
        updateHeaderStatus();
        updateSkillVisuals();
        startMatchIntro();
    }

    private void showHomeScreen() {
        matchStarted = false;
        lastTurnProgressIsX = null;
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);
    }

    private void startMatchIntro() {
        binding.homeOverlay.animate()
                .alpha(0f)
                .setDuration(460)
                .withEndAction(() -> {
                    binding.homeOverlay.setVisibility(View.GONE);
                    showVersusOverlay();
                })
                .start();
    }

    private void showVersusOverlay() {
        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(0f);

        String playerName = getPlayerDisplayName();

        binding.txtVersusX.setText(playerName);
        binding.txtVersusO.setText(opponentName);
        String modeLabel = selectedMode == GameMode.RANKED ? getString(R.string.mode_ranked_label) : getString(R.string.mode_casual_label);
        binding.txtVersusCenter.setText(modeLabel);

        binding.txtVersusX.setTranslationX(-220f);
        binding.txtVersusO.setTranslationX(220f);
        binding.txtVersusCenter.setScaleX(0.7f);
        binding.txtVersusCenter.setScaleY(0.7f);

        binding.versusOverlay.animate().alpha(1f).setDuration(160).start();

        binding.txtVersusX.animate()
                .translationX(0f)
                .setDuration(480)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        binding.txtVersusO.animate()
                .translationX(0f)
                .setDuration(480)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();

        binding.txtVersusCenter.animate()
                .scaleX(1.1f).scaleY(1.1f)
                .setDuration(240)
                .withEndAction(() -> binding.txtVersusCenter.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                .start();

        handler.postDelayed(() -> binding.versusOverlay.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> {
                    binding.versusOverlay.setVisibility(View.GONE);
                    matchStarted = true;
                    updateHeaderStatus();
                    updateSkillVisuals();
                    maybeRunBotTurn();
                })
                .start(), 1300);
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

    private void playTurn(int row, int col) {
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        if (beforeMoves == gameManager.getFinalMoves()) return;

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            drawVictoryLine();
            handler.postDelayed(() -> showVictoryScreen(symbol), 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            drawDrawLine();
            handler.postDelayed(this::showDrawScreen, 420);
            return;
        }

        maybeRunBotTurn();
    }

    private void maybeRunBotTurn() {
        if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
            return;
        }

        long thinkDelayMs = 900L + random.nextInt(700);

        handler.postDelayed(() -> {
            if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) {
                return;
            }

            if (currentBotDifficulty == Difficulty.MESTRE) {
                if (state.canUseTriangle() && random.nextFloat() < 0.35f && gameManager.useTriangle()) {
                        updateHeaderStatus();
                    updateSkillVisuals();
                    return;
                }
                if (state.canUseSquare() && random.nextFloat() < 0.25f && gameManager.useSquare()) {
                        updateHeaderStatus();
                    updateSkillVisuals();
                    return;
                }
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) {
                playTurn(move[0], move[1]);
            }
        }, thinkDelayMs);
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
        PointF topLeft = board.getCellCenterOnScreen(0, 0);
        PointF topRight = board.getCellCenterOnScreen(0, 2);
        PointF bottomLeft = board.getCellCenterOnScreen(2, 0);
        PointF bottomRight = board.getCellCenterOnScreen(2, 2);

        int[] lineLoc = new int[2];
        binding.victoryLineView.getLocationOnScreen(lineLoc);

        binding.victoryLineView.setDrawData(
                topLeft.x - lineLoc[0],
                topLeft.y - lineLoc[1],
                bottomRight.x - lineLoc[0],
                bottomRight.y - lineLoc[1],
                topRight.x - lineLoc[0],
                topRight.y - lineLoc[1],
                bottomLeft.x - lineLoc[0],
                bottomLeft.y - lineLoc[1]
        );
    }

    private void showDrawScreen() {
        binding.txtWinnerTitle.setText(R.string.game_draw);
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

    private void animateVictoryCard() {
        binding.victoryOverlay.setVisibility(View.VISIBLE);
        binding.victoryOverlay.setAlpha(0f);
        binding.victoryCard.setTranslationY(300f);

        binding.victoryOverlay.animate().alpha(1f).setDuration(280).start();
        binding.victoryCard.animate().translationY(0f).setDuration(520).setInterpolator(new OvershootInterpolator(1f)).start();
    }

    private void hideVictoryScreen() {
        binding.victoryOverlay.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> binding.victoryOverlay.setVisibility(View.GONE))
                .start();
    }

    public void updateHeaderStatus() {
        updateTurnHud();
    }

    private void updateTurnHud() {
        String playerName = getPlayerDisplayName();
        String rivalName = (opponentName == null || opponentName.isEmpty())
                ? getString(R.string.turn_hud_opponent_default)
                : opponentName;

        binding.txtTurnNameX.setText(playerName);
        binding.txtTurnNameO.setText(rivalName);

        if (!matchStarted || gameManager.isGameOver()) {
            lastTurnProgressIsX = null;
            stopTurnAnimator(true);
            stopTurnAnimator(false);
            binding.progressTurnX.setProgress(0);
            binding.progressTurnO.setProgress(0);
            styleTurnName(binding.txtTurnNameX, false);
            styleTurnName(binding.txtTurnNameO, false);
            return;
        }

        boolean isXTurn = state.isXTurn();
        styleTurnName(binding.txtTurnNameX, isXTurn);
        styleTurnName(binding.txtTurnNameO, !isXTurn);

        if (lastTurnProgressIsX == null || lastTurnProgressIsX != isXTurn) {
            startTurnAnimator(isXTurn);
            stopTurnAnimator(!isXTurn);
            lastTurnProgressIsX = isXTurn;
        }
    }

    private void styleTurnName(android.widget.TextView textView, boolean active) {
        textView.setTextColor(active ? 0xFFF8FAFC : 0xFF94A3B8);
        textView.setAlpha(active ? 1f : 0.75f);
        textView.animate()
                .scaleX(active ? 1.04f : 0.98f)
                .scaleY(active ? 1.04f : 0.98f)
                .setDuration(180)
                .start();
    }

    private void startTurnAnimator(boolean xTurn) {
        android.widget.ProgressBar active = xTurn ? binding.progressTurnX : binding.progressTurnO;
        active.setProgress(100);

        ObjectAnimator animator = ObjectAnimator.ofInt(active, "progress", 100, 0);
        animator.setDuration(TURN_PROGRESS_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.start();

        if (xTurn) {
            turnAnimatorX = animator;
            binding.progressTurnO.setProgress(0);
        } else {
            turnAnimatorO = animator;
            binding.progressTurnX.setProgress(0);
        }
    }

    private void stopTurnAnimator(boolean xTurn) {
        ObjectAnimator animator = xTurn ? turnAnimatorX : turnAnimatorO;
        if (animator != null) {
            animator.cancel();
        }
        if (xTurn) {
            turnAnimatorX = null;
        } else {
            turnAnimatorO = null;
        }
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() && matchStarted ? 1f : 0.25f;
        float sqAlpha = state.canUseSquare() && matchStarted ? 1f : 0.25f;

        binding.containerTriangle.animate().alpha(triAlpha).setDuration(220).start();
        binding.containerSquare.animate().alpha(sqAlpha).setDuration(220).start();

        binding.containerTriangle.setElevation(triAlpha == 1f ? 20f : 0f);
        binding.containerSquare.setElevation(sqAlpha == 1f ? 20f : 0f);
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
        stopTurnAnimator(true);
        stopTurnAnimator(false);
        super.onDestroy();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
}
