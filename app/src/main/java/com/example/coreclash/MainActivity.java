package com.example.coreclash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
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

import com.example.coreclash.billing.BillingManager;
import com.example.coreclash.data.FirebaseProfileRepository;
import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import economy.DailyBonus;
import economy.MatchRewards;
import enums.DomainBotNames;
import enums.DomainDifficulty;
import enums.DomainGameMode;
import enums.DomainSymbols;
import game.GameState;
import manager.AuthenticationManager;
import manager.BoardManager;
import manager.GameManager;
import manager.ProfileManager;
import manager.SettingManager;
import manager.StoreManager;
import manager.TurnHudManager;
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
    private BillingManager billingManager;
    private AuthenticationManager authenticationManager;
    private SettingManager settingManager;
    private TurnHudManager turnHud;

    private PlayerProfile currentProfile;
    private int lastMatchRewardCoins = 0;
    private DailyBonus.Grant pendingDailyGrant;

    private boolean matchStarted = false;
    private boolean versusBot = false;
    private Boolean lastTurnProgressIsX = null;

    private static final long TURN_PROGRESS_DURATION_MS = 10000L;
    private ObjectAnimator turnAnimatorX;
    private ObjectAnimator turnAnimatorO;
    private String opponentName = "";
    private DomainGameMode selectedMode = DomainGameMode.CASUAL;
    private DomainDifficulty currentBotDifficulty = DomainDifficulty.INICIANTE;

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
        turnHud = initTurnHudManager();

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

    @NonNull
    private TurnHudManager initTurnHudManager() {
        return new TurnHudManager(
                binding.turnHudBar,
                binding.txtTurnHudNameX,
                binding.txtTurnHudNameO,
                binding.progressTurnHudX,
                binding.progressTurnHudO,
                TURN_PROGRESS_DURATION_MS,
                (xTurnStarted) -> {
                    if (!matchStarted || gameManager.isGameOver()) {
                        return;
                    }
                    if (state.isXTurn() != xTurnStarted) {
                        return;
                    }

                    state.nextTurn();
                    updateHeaderStatus();
                    updateSkillVisuals();
                    maybeRunBotTurn();
                }
        );
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
            selectedMode = DomainGameMode.CASUAL;
            updateModeButtonStyles();
        });

        binding.btnModeRanked.setOnClickListener(v -> {
            selectedMode = DomainGameMode.RANKED;
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

        binding.btnNewMatch.setOnClickListener(v -> {
            hideVictoryScreen();
            startMatchmaking(false);
        });

        binding.btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            binding.victoryLineView.clear();
            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });

        binding.btnDailyCollect.setOnClickListener(v -> collectDailyBonus());
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
        boolean casual = selectedMode == DomainGameMode.CASUAL;
        // Visual states live in selector_btn_mode / selector_mode_text drawables.
        binding.btnModeCasual.setSelected(casual);
        binding.btnModeRanked.setSelected(!casual);
    }

    private void startMatchmaking(boolean fromOnlineButton) {
        boolean foundPlayer = fromOnlineButton && random.nextFloat() < 0.45f;
        // Every rival is engine-driven; "online" rivals are disguised stronger bots
        // so the match always has an opponent that actually plays.
        versusBot = true;

        if (foundPlayer) {
            opponentName = getString(R.string.online_rival_prefix) + (100 + random.nextInt(900));
            currentBotDifficulty = random.nextBoolean() ? DomainDifficulty.MODERADA : DomainDifficulty.MESTRE;

            String matchMsg = getString(R.string.toast_match_found, opponentName);
            Toast.makeText(this, matchMsg, Toast.LENGTH_SHORT).show();
        } else {
            opponentName = randomBotName();
            currentBotDifficulty = randomDifficulty();
        }

        state.setGameMode(selectedMode == DomainGameMode.RANKED ? GameState.GameMode.RANKED : GameState.GameMode.CASUAL);
        gameManager.resetGame();
        binding.victoryLineView.clear();
        setArenaUiVisible(false);
        updateHeaderStatus();
        updateSkillVisuals();
        startMatchIntro();
    }

    private void showHomeScreen() {
        matchStarted = false;
        lastTurnProgressIsX = null;
        setArenaUiVisible(true);
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);
        if (turnHud != null) {
            turnHud.stopAll();
        }
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
        String modeLabel = selectedMode == DomainGameMode.RANKED ? getString(R.string.mode_ranked_label) : getString(R.string.mode_casual_label);
        binding.txtVersusMode.setText(modeLabel);
        binding.txtVersusCenter.setText(getString(R.string.versus_title, playerName, opponentName));

        binding.txtVersusX.setTranslationX(-220f);
        binding.txtVersusO.setTranslationX(220f);
        binding.txtVersusCenter.setScaleX(0.8f);
        binding.txtVersusCenter.setScaleY(0.8f);
        binding.txtVersusMode.setAlpha(0f);
        binding.txtVersusMode.setTranslationY(-30f);
        binding.viewVersusStripeTop.setAlpha(0f);
        binding.viewVersusStripeBottom.setAlpha(0f);

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

        binding.txtVersusMode.animate().alpha(1f).translationY(0f).setDuration(360).start();
        binding.viewVersusStripeTop.animate().alpha(1f).setDuration(260).start();
        binding.viewVersusStripeBottom.animate().alpha(1f).setDuration(260).start();

        handler.postDelayed(() -> binding.versusOverlay.animate()
                .alpha(0f)
                .setDuration(240)
                .withEndAction(() -> {
                    binding.versusOverlay.setVisibility(View.GONE);
                    setArenaUiVisible(true);
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
            boolean playerWon = DomainSymbols.X.getValue().equals(symbol);
            applyMatchRewards(playerWon ? MatchRewards.Outcome.WIN : MatchRewards.Outcome.LOSS);
            drawVictoryLine();
            String winnerName = playerWon ? getPlayerDisplayName() : opponentName;
            handler.postDelayed(() -> showVictoryScreen(winnerName), 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            applyMatchRewards(MatchRewards.Outcome.DRAW);
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

            if (shouldBotUseSkill() && tryUseRandomBotSkill()) {
                updateHeaderStatus();
                updateSkillVisuals();
                return;
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) {
                playTurn(move[0], move[1]);
            }
        }, thinkDelayMs);
    }

    private boolean shouldBotUseSkill() {
        double chance = switch (currentBotDifficulty) {
            case INICIANTE -> 0.20;
            case MODERADA -> 0.55;
            case MESTRE -> 0.80;
        };
        return random.nextDouble() < chance;
    }

    private boolean tryUseRandomBotSkill() {
        boolean canTriangle = gameManager.canUseTriangleNow();
        boolean canSquare = gameManager.canUseSquareNow();
        if (!canTriangle && !canSquare) {
            return false;
        }

        if (canTriangle && canSquare) {
            return random.nextBoolean() ? gameManager.useTriangle() : gameManager.useSquare();
        }
        return canTriangle ? gameManager.useTriangle() : gameManager.useSquare();
    }

    private void setArenaUiVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        binding.turnHudBar.setVisibility(visibility);
        binding.containerTriangle.setVisibility(visibility);
        binding.lineLeftConnector.setVisibility(visibility);
        binding.boardContainer.setVisibility(visibility);
        binding.containerSquare.setVisibility(visibility);
        binding.lineRightConnector.setVisibility(visibility);
    }

    private int[] chooseBotMove(DomainDifficulty difficulty) {
        List<int[]> moves = gameManager.getAvailableMoves();
        if (moves.isEmpty()) {
            return null;
        }

        if (difficulty == DomainDifficulty.INICIANTE) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor(DomainSymbols.O.getValue());
        if (win != null) {
            return win;
        }

        int[] block = gameManager.findWinningMoveFor(DomainSymbols.X.getValue());
        if (block != null) {
            return block;
        }

        if (difficulty == DomainDifficulty.MODERADA) {
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
        binding.txtStatsReward.setText(getString(R.string.stats_reward, lastMatchRewardCoins));
        animateVictoryCard();
    }

    private void showVictoryScreen(String winnerName) {
        binding.txtWinnerTitle.setText(getString(R.string.game_win, winnerName));
        String winStats = getString(R.string.stats_wins_format, gameManager.getTotalWins());
        binding.txtStatsMoves.setText(getString(R.string.stats_moves, gameManager.getFinalMoves(), winStats));
        binding.txtStatsGhosts.setText(getString(R.string.stats_ghosts, gameManager.getFinalGhosts(), gameManager.getWinStreak()));
        binding.txtStatsReward.setText(getString(R.string.stats_reward, lastMatchRewardCoins));
        animateVictoryCard();
    }

    private void applyMatchRewards(MatchRewards.Outcome outcome) {
        boolean ranked = state.getGameMode() == GameState.GameMode.RANKED;
        lastMatchRewardCoins = MatchRewards.coinsFor(outcome, ranked, state.getWinStreak(), state.getMoveCount());

        if (currentProfile != null) {
            currentProfile.coins += lastMatchRewardCoins;
            currentProfile.totalWins = state.getTotalWins();
            currentProfile.winStreak = state.getWinStreak();
            currentProfile.bestWinStreak = state.getBestWinStreak();
            currentProfile.rankedPoints = state.getRankedPoints();
            profileManager.persistProfile();
        }
        updateHomeWallet();
    }

    public void updateHomeWallet() {
        if (currentProfile == null) {
            return;
        }
        binding.txtHomeWallet.setText(getString(R.string.home_wallet_format, currentProfile.coins, state.getRankLabel()));
        binding.txtHomeWallet.setVisibility(View.VISIBLE);
    }

    private void maybeShowDailyBonus() {
        if (currentProfile == null) {
            return;
        }
        long today = DailyBonus.epochDayOf(System.currentTimeMillis());
        pendingDailyGrant = DailyBonus.evaluate(currentProfile.lastDailyBonusEpochDay, currentProfile.dailyBonusStreak, today);
        if (pendingDailyGrant == null) {
            return;
        }

        binding.txtDailyBonusDay.setText(getString(R.string.daily_bonus_day, pendingDailyGrant.streakDay()));
        binding.txtDailyBonusCoins.setText(getString(R.string.daily_bonus_coins, pendingDailyGrant.coins()));

        binding.dailyOverlay.setVisibility(View.VISIBLE);
        binding.dailyOverlay.setAlpha(0f);
        binding.dailyCard.setScaleX(0.85f);
        binding.dailyCard.setScaleY(0.85f);

        binding.dailyOverlay.animate().alpha(1f).setDuration(200).start();
        binding.dailyCard.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .setDuration(300)
                .start();
    }

    private void collectDailyBonus() {
        if (pendingDailyGrant == null || currentProfile == null) {
            return;
        }
        currentProfile.coins += pendingDailyGrant.coins();
        currentProfile.dailyBonusStreak = pendingDailyGrant.streakDay();
        currentProfile.lastDailyBonusEpochDay = DailyBonus.epochDayOf(System.currentTimeMillis());
        pendingDailyGrant = null;

        profileManager.persistProfile();
        updateHomeWallet();

        binding.dailyOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> binding.dailyOverlay.setVisibility(View.GONE))
                .start();
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

        binding.txtTurnHudNameX.setText(playerName);
        binding.txtTurnHudNameO.setText(rivalName);

        if (!matchStarted || gameManager.isGameOver()) {
            lastTurnProgressIsX = null;
            stopTurnAnimator(true);
            stopTurnAnimator(false);
            binding.progressTurnHudX.setProgress(0);
            binding.progressTurnHudO.setProgress(0);
            styleTurnName(binding.txtTurnHudNameX, false);
            styleTurnName(binding.txtTurnHudNameO, false);
            return;
        }

        boolean isXTurn = state.isXTurn();
        styleTurnName(binding.txtTurnHudNameX, isXTurn);
        styleTurnName(binding.txtTurnHudNameO, !isXTurn);

        if (lastTurnProgressIsX == null || lastTurnProgressIsX != isXTurn) {
            startTurnAnimator(isXTurn);
            stopTurnAnimator(!isXTurn);
            lastTurnProgressIsX = isXTurn;
        }
    }

    private void styleTurnName(android.widget.TextView textView, boolean active) {
        textView.setTextColor(active ? 0xFFFFFFFF : 0xFFB6C2D1);
        textView.setAlpha(active ? 1f : 0.8f);
        textView.animate()
                .scaleX(active ? 1.03f : 1f)
                .scaleY(active ? 1.03f : 1f)
                .setDuration(160)
                .start();
    }

    private void startTurnAnimator(boolean xTurn) {
        android.widget.ProgressBar active = xTurn ? binding.progressTurnHudX : binding.progressTurnHudO;
        active.setProgress(100);

        ObjectAnimator animator = ObjectAnimator.ofInt(active, "progress", 100, 0);
        animator.setDuration(TURN_PROGRESS_DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    onTurnTimerElapsed(xTurn);
                }
            }
        });
        animator.start();

        if (xTurn) {
            turnAnimatorX = animator;
            binding.progressTurnHudO.setProgress(0);
        } else {
            turnAnimatorO = animator;
            binding.progressTurnHudX.setProgress(0);
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

    private void onTurnTimerElapsed(boolean xTurnTurnStarted) {
        if (!matchStarted || gameManager.isGameOver()) {
            return;
        }
        if (state.isXTurn() != xTurnTurnStarted) {
            return;
        }

        state.nextTurn();
        updateHeaderStatus();
        updateSkillVisuals();
        maybeRunBotTurn();
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
        DomainBotNames[] values = DomainBotNames.values();
        return values[random.nextInt(values.length)].getDisplayName();
    }

    private DomainDifficulty randomDifficulty() {
        DomainDifficulty[] levels = DomainDifficulty.values();
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

        state.restoreProgress(profile.totalWins, profile.winStreak, profile.bestWinStreak, profile.rankedPoints);

        billingManager = new BillingManager();
        storeManager = new StoreManager(this, binding, profile, profileManager, board, billingManager);
        billingManager.start(this, amount -> runOnUiThread(() -> storeManager.grantCoins(amount)));

        runOnUiThread(() -> {
            storeManager.applyEquippedCosmetics();
            updateHomeWallet();
            updateHeaderStatus();
            updateSkillVisuals();
            maybeShowDailyBonus();
        });
    }

    private void loadLocalFallback() {
        var localRepo = new LocalProfileRepository(this);

        localRepo.loadOrCreateProfile(new ProfileRepository.Callback() {
            @Override
            public void onSuccess(@NonNull PlayerProfile profile) {
                onProfileReady(profile);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.toast_offline_loaded), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(@NonNull String error) {
                Log.e("Fallback", "Critical error");
                onProfileReady(PlayerProfile.createDefault("temp_" + System.currentTimeMillis()));
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
