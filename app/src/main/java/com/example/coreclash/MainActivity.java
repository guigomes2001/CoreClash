package com.example.coreclash;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;

import enums.DomainMatchPhase;
import enums.DomainSymmetries;
import ui.anim.MatchIntroAnimator;
import ui.anim.TimeoutBannerAnimator;
import ui.anim.VictoryOverlayAnimator;
import ui.anim.flow.HomeFlowManager;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Random;

import enums.DomainBotNames;
import enums.DomainDifficulty;
import enums.DomainGameMode;
import game.GameState;
import manager.AuthenticationManager;
import manager.BoardManager;
import manager.BotManager;
import manager.GameManager;
import manager.HomeAwayManager;
import manager.MatchManager;
import manager.PlayerServicesManager;
import manager.SettingManager;
import manager.StoreManager;
import manager.SocialManager;
import manager.TurnHudManager;
import util.AnimationHelper;
import util.FontAwesomeIconFactory;
import util.NullUtil;
import util.SafeClickUtil;

public class MainActivity extends AppCompatActivity {

    public final Handler handler = new Handler(Looper.getMainLooper());
    private static final Random random = new Random();

    private ActivityMainBinding binding;

    private GameManager gameManager;
    private GameState state;
    private BoardManager board;

    private StoreManager storeManager;
    private AuthenticationManager authenticationManager;
    private SettingManager settingManager;
    private TurnHudManager turnHud;

    private PlayerServicesManager playerServices;
    private HomeFlowManager homeFlow;

    private PlayerProfile currentProfile;

    private boolean matchStarted = false;
    private boolean versusBot = false;
    private boolean passAndPlayMode = false;
    private DomainMatchPhase matchPhase = DomainMatchPhase.LOADING;

    private boolean bothIntroReady = false;

    private static final long TURN_PROGRESS_DURATION_MS = 10000L;
    private static final int ROUNDS_TO_WIN = 2;
    private static final long SKILL_ACTION_LOCK_MS = 650L;

    private String opponentName = "";
    private int roundsWonX = 0;
    private int roundsWonO = 0;
    private long actionLockedUntilMs = 0L;
    private final String selectedMode = DomainGameMode.CASUAL.getValue();
    private DomainDifficulty currentBotDifficulty = DomainDifficulty.BEGINNER;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private MatchManager matchManager;
    private BotManager botManager;
    private HomeAwayManager homeAwayManager;
    private SocialManager socialManager;

    private TimeoutBannerAnimator timeoutBannerAnimator;
    private MatchIntroAnimator matchIntroAnimator;
    private VictoryOverlayAnimator victoryOverlayAnimator;
    private boolean passPlayPlayerOneIsX = true;
    private int countdownRunToken = 0;
    private String lastMatchmakingStatus = "";
    private long lastUiToastAtMs = 0L;
    private String lastUiToastMessage = "";
    private boolean arenaVisibilityApplying = false;
    private String matchmakingBaseStatus = "";
    private int matchmakingDotsPhase = 0;
    private final Runnable matchmakingStatusTicker = new Runnable() {
        @Override
        public void run() {
            if (NullUtil.isNull(binding) || binding.matchmakingOverlay.getVisibility() != View.VISIBLE) return;
            String dots = switch (matchmakingDotsPhase % 4) {
                case 1 -> ".";
                case 2 -> "..";
                case 3 -> "...";
                default -> "";
            };
            binding.txtMatchmakingDots.setText(dots);
            matchmakingDotsPhase++;
            handler.postDelayed(this, 360);
        }
    };

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

        timeoutBannerAnimator = new TimeoutBannerAnimator(binding);
        victoryOverlayAnimator = new VictoryOverlayAnimator(binding, board, gameManager, handler);
        settingManager = new SettingManager(this, binding);
        homeAwayManager = new HomeAwayManager(this);
        socialManager = new SocialManager();

        turnHud = initTurnHudManager();

        homeFlow = initHomeFlow();
        homeFlow.setSelectedMatchKind(enums.DomainMatchKind.OFFLINE_BOT);
        homeFlow.bind();
        SafeClickUtil.setSafeClick(binding.btnProfile, 320, v -> openProfileDialog());
        SafeClickUtil.setSafeClick(binding.btnFriends, 320, v -> openFriendsDialog());

        playerServices = initPlayerServices();
        botManager = initBotManager();
        matchIntroAnimator = initMatchIntroAnimator();
        matchManager = initMatchManager();

        board.createBoard(this, binding.gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) {
                return;
            }

            if (isActionLocked()) {
                AnimationHelper.shakeButton(binding.turnHudBar);
                return;
            }

            if (matchManager.isOnlineMatch()) {
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!matchManager.isMyTurnOnline()) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                playTurnLocalAndSend(row, col);
                return;
            }

            if (versusBot && !state.isXTurn()) {
                return;
            }

            playTurn(row, col);
        });

        setupSkills();
        setupMetaControls();
        configureMatchmakingOverlay();
        SafeClickUtil.setSafeClick(binding.btnMatchmakingCancel, 420, v -> cancelMatchmakingSearch());

        playerServices.start();
        String myUid = getMyUidOrNull();
        if (!NullUtil.isNull(myUid)) {
            socialManager.setPresence(myUid, "online");
            socialManager.upsertUserProfile(myUid, getPlayerDisplayName(), buildTagFromUid(myUid));
        }

        opponentName = getString(R.string.status_waiting);

        showHomeScreen();
        homeFlow.updateModeButtonStyles();
        updateHeaderStatus();
        updateSkillVisuals();
    }

    private BotManager initBotManager() {
        return new BotManager(
                handler,
                random,
                gameManager,
                new BotManager.Gate() {
                    @Override public boolean isOnlineMatch() { return !NullUtil.isNull(matchManager) && matchManager.isOnlineMatch(); }
                    @Override public boolean isVersusBot() { return versusBot; }
                    @Override public boolean isMatchStarted() { return matchStarted; }
                    @Override public boolean isXTurn() { return state.isXTurn(); }
                    @Override public boolean isGameOver() { return gameManager.isGameOver(); }
                    @Override public boolean isActionLocked() { return MainActivity.this.isActionLocked(); }
                    @NonNull @Override public DomainDifficulty getDifficulty() { return currentBotDifficulty; }
                },
                new BotManager.Callbacks() {
                    @Override public void onRender() {
                        updateHeaderStatus();
                        updateSkillVisuals();
                    }

                    @Override public void onBotPlayMove(int r, int c) {
                        playTurn(r, c);
                    }
                }
        );
    }

    private HomeFlowManager initHomeFlow() {
        return new HomeFlowManager(binding, new HomeFlowManager.Callbacks() {
            @Override public void onQuickPlayClicked() {
                startOfflineVsBot();
            }

            @Override public void onPlayOnlineClicked() {
                if (!ensureInternetForOnlineModes()) return;

                homeFlow.closeModeModal();

                String uid = getMyUidOrNull();
                if (NullUtil.isNull(uid)) {
                    Toast.makeText(MainActivity.this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
                    return;
                }

                showMatchmakingLoading(getString(R.string.toast_looking_match));
                matchManager.startOnlineMatchmaking(() -> uid);
            }

            @Override public void onStoreClicked() {
                if (NullUtil.isNull(storeManager)) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_offline_loaded), Toast.LENGTH_SHORT).show();
                    return;
                }
                storeManager.openStore();
            }

            @Override public void onSettingsClicked() {
                settingManager.openSettings();
            }

            @Override public void onGoogleLoginFromSettingsClicked() {
                settingManager.closeSettings();
                startGoogleSignIn();
            }

            @Override public void onConfirmOfflineVsBot() {
                startOfflineVsBot();
            }

            @Override public void onConfirmOnlinePvp() {
                if (!ensureInternetForOnlineModes()) return;

                homeFlow.closeModeModal();

                String uid = getMyUidOrNull();
                if (NullUtil.isNull(uid)) {
                    Toast.makeText(MainActivity.this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
                    return;
                }
                showMatchmakingLoading(getString(R.string.toast_looking_match));
                matchManager.startOnlineMatchmaking(() -> uid);
            }

            @Override public void onConfirmLocalPassPlay() {
                startLocalPassAndPlay();
            }

            @Override public void onConfirmLocalLobby() {
                openLocalLobbyDialog();
            }

            @Override public void onModeChanged(@NonNull enums.DomainMatchKind selected) {

            }
        });
    }

    private PlayerServicesManager initPlayerServices() {
        return new PlayerServicesManager(
                this,
                this::runOnUiThread,
                binding,
                board,
                handler,
                new PlayerServicesManager.Callbacks() {
                    @Override public void onProfileReady(@NonNull PlayerProfile profile) {
                        currentProfile = profile;
                        String uid = getMyUidOrNull();
                        if (!NullUtil.isNull(uid)) {
                            socialManager.upsertUserProfile(uid, profile.displayName, buildTagFromUid(uid));
                        }
                    }
                    @Override public void onStoreReady(@NonNull StoreManager sm) { storeManager = sm; }
                    @Override public void onRender() {
                        updateHeaderStatus();
                        updateSkillVisuals();
                    }
                }
        );
    }

    @NonNull
    private String getOpponentDisplayName() {
        if (passAndPlayMode) {
            return getString(R.string.label_player_two);
        }

        if (!NullUtil.isNull(matchManager) && matchManager.isOnlineMatch()) {
            String n = matchManager.getOpponentName();
            if (!NullUtil.isNull(n) && !n.trim().isEmpty()) {
                return n.trim();
            }
            return getString(R.string.status_waiting_opponent);
        }

        if (!NullUtil.isNull(opponentName) && !opponentName.trim().isEmpty()) {
            return opponentName.trim();
        }
        return getString(R.string.turn_hud_opponent_default);
    }

    private MatchIntroAnimator initMatchIntroAnimator() {
        return new MatchIntroAnimator(
                binding,
                handler,
                new MatchIntroAnimator.Callbacks() {
                    @NonNull @Override public String getPlayerName() { return getPlayerDisplayName(); }

                    @NonNull @Override public String getOpponentName() { return getOpponentDisplayName(); }

                    @NonNull @Override public String getModeLabel() {
                        return selectedMode.equalsIgnoreCase(DomainGameMode.RANKED.getValue())
                                ? getString(R.string.mode_ranked_label)
                                : getString(R.string.mode_casual_label);
                    }


                    @NonNull @Override public String getBreakSymbolLeft() {
                        if (passAndPlayMode) return "△";
                        if (matchManager.isOnlineMatch() && !matchManager.isMyTurnOnline()) return "O";
                        return "X";
                    }

                    @NonNull @Override public String getBreakSymbolRight() {
                        if (passAndPlayMode) return "□";
                        if (matchManager.isOnlineMatch() && !matchManager.isMyTurnOnline()) return "X";
                        return "O";
                    }

                    @Override public void setArenaUiVisible(boolean visible) {
                        MainActivity.this.applyArenaUiVisibility(visible);
                    }

                    @Override public void onIntroFinished() {
                        if (matchManager.isOnlineMatch()) {
                            matchStarted = false;
                            matchPhase = DomainMatchPhase.LOADING;
                            bothIntroReady = false;
                            updateHeaderStatus();
                            updateSkillVisuals();

                            if (!NullUtil.isNull(matchManager.getOnlineSession())) {
                                matchManager.getOnlineSession().markIntroReady(() -> runOnUiThread(() -> {
                                    bothIntroReady = true;

                                    matchManager.getOnlineSession().startPlayingWhenIntroFinished();

                                    runCountdown(getOnlineStartsLabel(), () -> beginPlayingAfterCountdown(true));
                                }));
                            }
                            return;
                        }

                        runCountdown(getLocalStartsLabel(), () -> beginPlayingAfterCountdown(false));
                    }
                }
        );
    }

    @NonNull
    private MatchManager initMatchManager() {
        return new MatchManager(
                this,
                handler,
                binding,
                gameManager,
                new MatchManager.Callbacks() {
                    @Override public void runOnUi(@NonNull Runnable r) { runOnUiThread(r); }
                    @Override public void onUpdateHeaderStatus() { updateHeaderStatus(); }
                    @Override public void onUpdateSkillVisuals() { updateSkillVisuals(); }
                    @Override public void onShowWaitingOpponentUi() {
                        matchStarted = false;
                        bothIntroReady = false;

                        opponentName = getString(R.string.status_waiting_opponent);
                        updateHeaderStatus();

                        showMatchmakingLoading(getString(R.string.status_waiting_opponent));
                        homeFlow.showWaitingOpponentUi();
                    }
                    @Override public void onRestoreMenuButtons() {
                        hideMatchmakingLoading();
                        homeFlow.restoreMenuButtons();
                    }
                    @Override public void onSetArenaUiVisible(boolean visible) {
                        MainActivity.this.applyArenaUiVisibility(visible);
                    }

                    @Override public void onBeforeOnlineMatchStart() {
                        versusBot = false;
                        passAndPlayMode = false;
                        matchPhase = DomainMatchPhase.LOADING;
                        bothIntroReady = false;

                        setGameMode();
                        resetRoundSeries();
                        state.setGameMode(DomainGameMode.ONLINE.getValue());
                        gameManager.resetGame();
                        victoryOverlayAnimator.clearLines();

                        opponentName = matchManager.getOpponentName();
                    }

                    @Override public void onPlayTimeoutBanner(boolean xSide) { timeoutBannerAnimator.play(xSide); }
                    @Override public void onHideTimeoutBanner(boolean xSide) { timeoutBannerAnimator.hide(xSide); }

                    @Override public boolean isBothIntroReady() { return bothIntroReady; }

                    @Override public void onOnlineMatchShouldStartPlaying() {
                        hideMatchmakingLoading();
                        matchPhase = DomainMatchPhase.COUNTDOWN;
                        matchIntroAnimator.startFromHome();
                    }

                    @Override
                    public void onOnlineRemoteWin(@NonNull String winnerSymbol) {
                        handleRoundFinished(winnerSymbol, false);
                    }

                    @Override
                    public void onOnlineRemoteDraw() {
                        handleRoundFinished(null, true);
                    }

                    @Override public void onEndOnlineSessionToMenu() { onlineSessionEndedCleanup(); }
                });
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
                    if (matchManager.isOnlineMatch()) {
                        return;
                    }
                    if (!matchStarted || gameManager.isGameOver()) {
                return;
            }
                    if (state.isXTurn() != xTurnStarted) {
                        return;
                    }

                    boolean forfeit = state.registerTimeout(xTurnStarted);

                    if (forfeit) {
                        matchStarted = false;
                        String winner = xTurnStarted ? DomainSymmetries.O.getValue() : DomainSymmetries.X.getValue();
                        victoryOverlayAnimator.showWin(winner, 300);
                        return;
                    }

                    timeoutBannerAnimator.play(xTurnStarted);
                    state.nextTurn();
                    updateHeaderStatus();
                    updateSkillVisuals();
                    botManager.maybeRunBotTurn();
                }
        );
    }

    private void setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && !NullUtil.isNull(result.getData())) {
                        authenticationManager.handleSignInResult(result.getData(), new AuthenticationManager.AuthCallback() {
                            @Override public void onGoogleLinked(String displayName) {
                                playerServices.updateDisplayNameAndPersist(displayName);
                                updateHeaderStatus();
                                Toast.makeText(MainActivity.this, getString(R.string.toast_progress_linked), Toast.LENGTH_SHORT).show();
                            }

                            @Override public void onFailure(String message) {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
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

            if (matchManager.isOnlineMatch()) {
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!matchManager.isMyTurnOnline()) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
            }

            if (isActionLocked()) {
                AnimationHelper.shakeButton(binding.turnHudBar);
                return;
            }

            if (versusBot && !state.isXTurn()) {
                AnimationHelper.shakeButton(binding.turnHudBar);
                return;
            }

            boolean actingX = state.isXTurn();

            if (gameManager.useTriangle()) {
                state.resetTimeoutStreak(actingX);
                lockActionsTemporarily(SKILL_ACTION_LOCK_MS);

                AnimationHelper.spin(v);

                if (matchManager.isOnlineMatch()) {
                    matchManager.sendTriangle();
                }

                updateHeaderStatus();
                updateSkillVisuals();

                if (!matchManager.isOnlineMatch()) botManager.maybeRunBotTurn();
            }
        });

        binding.containerSquare.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseSquare()) {
                AnimationHelper.shakeButton(v);
                return;
            }

            if (matchManager.isOnlineMatch()) {
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!matchManager.isMyTurnOnline()) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
            }

            if (isActionLocked()) {
                AnimationHelper.shakeButton(binding.turnHudBar);
                return;
            }

            if (versusBot && !state.isXTurn()) {
                AnimationHelper.shakeButton(binding.turnHudBar);
                return;
            }

            boolean actingX = state.isXTurn();

            if (gameManager.useSquare()) {
                state.resetTimeoutStreak(actingX);
                lockActionsTemporarily(SKILL_ACTION_LOCK_MS);

                AnimationHelper.pulse(v);

                if (matchManager.isOnlineMatch()) {
                    matchManager.sendSquare();
                }

                updateHeaderStatus();
                updateSkillVisuals();

                if (!matchManager.isOnlineMatch()) botManager.maybeRunBotTurn();
            }
        });
    }


    private void configureMatchmakingOverlay() {
        Drawable icon = FontAwesomeIconFactory.createDrawable(this, getString(R.string.fa_xmark), 15, 0xFFEAF2FF);
        binding.btnMatchmakingCancel.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        binding.btnMatchmakingCancel.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
        binding.btnMatchmakingCancel.setEnabled(false);
        binding.lottieMatchmaking.setSpeed(1.06f);
        binding.lottieMatchmaking.setProgress(0f);
        binding.lottieMatchmaking.setRepeatCount(-1);
    }

    private void setupMetaControls() {
        SafeClickUtil.setSafeClick(binding.btnRestart, 320, v -> {
            victoryOverlayAnimator.hide();
            matchPhase = DomainMatchPhase.LOADING;
            gameManager.resetGame();
            victoryOverlayAnimator.clearLines();
            updateSkillVisuals();

            botManager.cancelPending();

            if (matchManager.isOnlineMatch()) {
                matchManager.endOnlineSessionToMenu();
                return;
            }

            startRematchIntro();
        });

        SafeClickUtil.setSafeClick(binding.btnExit, 320, v -> {
            victoryOverlayAnimator.hide();
            matchPhase = DomainMatchPhase.LOADING;
            gameManager.resetGame();
            victoryOverlayAnimator.clearLines();

            botManager.cancelPending();

            if (matchManager.isOnlineMatch()) {
                matchManager.endOnlineSessionToMenu();
                return;
            }

            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void onlineSessionEndedCleanup() {
        matchStarted = false;
        versusBot = false;
        passAndPlayMode = false;
        matchPhase = DomainMatchPhase.LOADING;
        bothIntroReady = false;

        botManager.cancelPending();
        matchIntroAnimator.cancel();
        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        showHomeScreen();
        updateHeaderStatus();
        updateSkillVisuals();
    }

    private void playTurnLocalAndSend(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(r, c);

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        matchManager.sendMove(r, c);

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            handleRoundFinished(symbol, false);
            return;
        }

        if (gameManager.isGameOver()) {
            handleRoundFinished(null, true);
        }
    }

    private void beginPlayingAfterCountdown(boolean onlineMatch) {
        matchStarted = true;
        matchPhase = DomainMatchPhase.PLAYING;
        updateHeaderStatus();
        updateSkillVisuals();

        if (onlineMatch) {
            matchManager.onBothIntroReady();
            return;
        }
        botManager.maybeRunBotTurn();
    }

    private void showUiToastDeduped(@NonNull String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastUiToastMessage) && (now - lastUiToastAtMs) < 1200L) return;
        lastUiToastMessage = message;
        lastUiToastAtMs = now;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showUiToastDedupedStyled(@NonNull String message, @NonNull String iconGlyph, int iconColor) {
        long now = System.currentTimeMillis();
        if (message.equals(lastUiToastMessage) && (now - lastUiToastAtMs) < 1200L) return;
        lastUiToastMessage = message;
        lastUiToastAtMs = now;

        View content = getLayoutInflater().inflate(R.layout.toast_system_message, null, false);
        TextView icon = content.findViewById(R.id.txtToastIcon);
        TextView text = content.findViewById(R.id.txtToastMessage);
        icon.setText(iconGlyph);
        icon.setTextColor(iconColor);
        text.setText(message);

        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(content);
        toast.show();
    }

    private void setGameMode() {
        String gameMode = selectedMode.equalsIgnoreCase(DomainGameMode.RANKED.getValue())
                ? DomainGameMode.RANKED.getValue()
                : DomainGameMode.CASUAL.getValue();
        state.setGameMode(gameMode);
    }


    private void runCountdown(@NonNull String startsLabel, @NonNull Runnable onFinish) {
        countdownRunToken++;
        int localToken = countdownRunToken;

        matchPhase = DomainMatchPhase.COUNTDOWN;
        binding.countdownOverlay.setVisibility(View.VISIBLE);
        binding.countdownOverlay.setAlpha(0f);
        binding.countdownOverlay.animate().alpha(1f).setDuration(140).start();
        updateCountdownStartsLabel(startsLabel);

        final long tickDelayMs = 900L;
        final long goHoldMs = 320L;
        int[] ticks = {3, 2, 1};
        for (int i = 0; i < ticks.length; i++) {
            int value = ticks[i];
            long delay = i * tickDelayMs;
            handler.postDelayed(() -> {
                if (localToken != countdownRunToken) return;
                binding.txtCountdownValue.setText(String.valueOf(value));
            }, delay);
        }

        handler.postDelayed(() -> {
            if (localToken != countdownRunToken) return;
            binding.txtCountdownValue.setText(getString(R.string.countdown_go));
            onFinish.run();
        }, ticks.length * tickDelayMs);

        handler.postDelayed(() -> {
            if (localToken != countdownRunToken) return;
            binding.countdownOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                if (localToken != countdownRunToken) return;
                binding.countdownOverlay.setVisibility(View.GONE);
                binding.countdownOverlay.setAlpha(1f);
            }).start();
        }, ticks.length * tickDelayMs + goHoldMs);
    }

    private void updateCountdownStartsLabel(@NonNull String startsLabel) {
        binding.txtCountdownWhoStarts.setText(startsLabel);
        String lower = startsLabel.toLowerCase();
        boolean opponentStarts = lower.contains("opponent") || lower.contains("advers");
        binding.txtCountdownWhoStarts.setTextColor(
                opponentStarts ? Color.parseColor("#FFD6E2") : Color.parseColor("#D9FCFF")
        );
    }

    @NonNull
    private String getOnlineStartsLabel() {
        return matchManager.isMyTurnOnline()
                ? getString(R.string.countdown_you_start)
                : getString(R.string.countdown_opponent_starts);
    }

    @NonNull
    private String getLocalStartsLabel() {
        if (passAndPlayMode) {
            boolean playerOneTurn = (passPlayPlayerOneIsX && state.isXTurn()) || (!passPlayPlayerOneIsX && !state.isXTurn());
            String who = playerOneTurn ? getString(R.string.label_player_one) : getString(R.string.label_player_two);
            return getString(R.string.countdown_player_starts, who);
        }
        return state.isXTurn() ? getString(R.string.countdown_you_start) : getString(R.string.countdown_opponent_starts);
    }

    private void startLocalPassAndPlay() {
        hideMatchmakingLoading();
        passAndPlayMode = true;
        versusBot = false;
        matchPhase = DomainMatchPhase.LOADING;
        passPlayPlayerOneIsX = homeAwayManager.chooseHome(getString(R.string.label_player_one), getString(R.string.label_player_two));
        opponentName = getString(R.string.label_player_two);
        resetRoundSeries();

        setGameMode();
        state.setGameMode(DomainGameMode.LOCAL_PASS_PLAY.getValue());
        gameManager.resetGame();
        state.setXTurn(true);
        victoryOverlayAnimator.clearLines();
        applyArenaUiVisibility(false);

        updateHeaderStatus();
        updateSkillVisuals();
        matchIntroAnimator.startFromHome();
    }

    private void applyDialogStyle(@NonNull AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (!NullUtil.isNull(window)) window.setBackgroundDrawableResource(R.drawable.bg_cyber_glass);

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        if (!NullUtil.isNull(positive)) {
            positive.setAllCaps(false);
            positive.setTextColor(Color.parseColor("#6EE7FF"));
        }
        if (!NullUtil.isNull(negative)) {
            negative.setAllCaps(false);
            negative.setTextColor(Color.parseColor("#D8E9FF"));
        }
        if (!NullUtil.isNull(neutral)) {
            neutral.setAllCaps(false);
            neutral.setTextColor(Color.parseColor("#D8E9FF"));
        }
    }

    private void styleInput(@NonNull EditText input) {
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        input.setTextColor(Color.parseColor("#EAF2FF"));
        input.setHintTextColor(Color.parseColor("#8FAACA"));
        input.setBackgroundResource(R.drawable.bg_button_tertiary);
        input.setPadding(pad, pad, pad, pad);
    }

    private void openLocalLobbyDialog() {
        if (!ensureInternetForOnlineModes()) return;

        String uid = getMyUidOrNull();
        if (NullUtil.isNull(uid)) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog localLobbyDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mode_local_lobby))
                .setMessage(getString(R.string.local_lobby_enter_code))
                .setPositiveButton(getString(R.string.local_lobby_create), (d, w) -> {
                    String code = generateRoomCode();
                    showMatchmakingLoading(getString(R.string.local_lobby_waiting));
                    Toast.makeText(this, getString(R.string.local_lobby_host_code, code), Toast.LENGTH_LONG).show();
                    matchManager.createLocalLobby(uid, code);
                })
                .setNegativeButton(getString(R.string.local_lobby_join), (d, w) -> {
                    EditText input = new EditText(this);
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    input.setHint(getString(R.string.hint_room_code_example));
                    styleInput(input);
                    AlertDialog joinDialog = new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.local_lobby_enter_code))
                            .setView(input)
                            .setPositiveButton(getString(R.string.local_lobby_join), (d2, w2) -> {
                                String code = NullUtil.isNull(input.getText()) ? "" : input.getText().toString().trim().toUpperCase();
                                if (code.length() < 4) {
                                    Toast.makeText(this, getString(R.string.error_invalid_room_code), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                showMatchmakingLoading(getString(R.string.local_lobby_waiting));
                                matchManager.joinLocalLobby(uid, code);
                            })
                            .setNegativeButton(getString(R.string.btn_back), null)
                            .create();
                    joinDialog.show();
                    applyDialogStyle(joinDialog);
                })
                .create();
        localLobbyDialog.show();
        applyDialogStyle(localLobbyDialog);
    }

    @NonNull
    private String generateRoomCode() {
        final String alphabet = getString(R.string.room_code_alphabet);
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }


    private void cancelMatchmakingSearch() {
        boolean hadOnlineSession = matchManager.isOnlineMatch();
        matchManager.cancelMatchmakingSearch();

        hideMatchmakingLoading();
        homeFlow.restoreMenuButtons();
        binding.modeOverlay.setVisibility(View.GONE);
        binding.settingsOverlay.setVisibility(View.GONE);

        if (!hadOnlineSession) {
            applyArenaUiVisibility(false);
            binding.homeOverlay.setVisibility(View.VISIBLE);
            binding.homeOverlay.setAlpha(1f);
            updateHeaderStatus();
            updateSkillVisuals();
        }

        showUiToastDedupedStyled(getString(R.string.toast_matchmaking_canceled), getString(R.string.fa_xmark), 0xFFFF7A8C);
    }

    private void showMatchmakingLoading(@NonNull String statusText) {
        lastMatchmakingStatus = statusText;
        matchmakingDotsPhase = 0;
        String stableStatus = statusText.replace("…", "").replace("...", "");
        binding.txtMatchmakingStatus.setText(stableStatus);
        binding.txtMatchmakingDots.setText("");
        binding.btnMatchmakingCancel.setEnabled(true);

        if (binding.matchmakingOverlay.getVisibility() != View.VISIBLE) {
            binding.matchmakingOverlay.setVisibility(View.VISIBLE);
            binding.matchmakingOverlay.setAlpha(0f);
            binding.matchmakingOverlay.animate().alpha(1f).setDuration(180).start();
        }

        if (!binding.lottieMatchmaking.isAnimating()) {
            binding.lottieMatchmaking.playAnimation();
        }

        handler.removeCallbacks(matchmakingStatusTicker);
        handler.post(matchmakingStatusTicker);

        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        applyArenaUiVisibility(false);
    }

    private void hideMatchmakingLoading() {
        handler.removeCallbacks(matchmakingStatusTicker);

        if (binding.matchmakingOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.matchmakingOverlay.animate()
                .alpha(0f)
                .setDuration(140)
                .withEndAction(() -> {
                    binding.matchmakingOverlay.setVisibility(View.GONE);
                    binding.btnMatchmakingCancel.setEnabled(false);
                    binding.lottieMatchmaking.cancelAnimation();
                    binding.txtMatchmakingDots.setText("");
                    binding.matchmakingOverlay.setAlpha(1f);
                    lastMatchmakingStatus = "";
                    matchmakingBaseStatus = "";
                })
                .start();
    }

    private void showHomeScreen() {
        matchStarted = false;
        countdownRunToken++;
        binding.countdownOverlay.setVisibility(View.GONE);

        botManager.cancelPending();
        matchManager.stopOnlineBarAnim(true);
        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        hideMatchmakingLoading();
        applyArenaUiVisibility(false);
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);

        binding.homeOverlay.post(homeFlow::playHomeEntrance);
    }

    private String getPlayerDisplayName() {
        if (passAndPlayMode) {
            return getString(R.string.label_player_one);
        }

        if (!NullUtil.isNull(currentProfile) && !NullUtil.isNull(currentProfile.displayName) && !currentProfile.displayName.isEmpty()) {
            return currentProfile.displayName;
        }

        var user = FirebaseAuth.getInstance().getCurrentUser();
        if (!NullUtil.isNull(user) && !NullUtil.isNull(user.getDisplayName()) && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        return getString(R.string.default_player_name);
    }

    private String getMyUidOrNull() {
        var u = FirebaseAuth.getInstance().getCurrentUser();
        return NullUtil.isNull(u) ? null : u.getUid();
    }

    private void playTurn(int row, int col) {
        boolean actingX = state.isXTurn();
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        state.resetTimeoutStreak(actingX);
        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            handleRoundFinished(symbol, false);
            return;
        }

        if (gameManager.isGameOver()) {
            handleRoundFinished(null, true);
            return;
        }

        botManager.maybeRunBotTurn();
    }

    private void resetRoundSeries() {
        roundsWonX = 0;
        roundsWonO = 0;
    }

    private boolean isActionLocked() {
        return System.currentTimeMillis() < actionLockedUntilMs;
    }

    private void lockActionsTemporarily(long durationMs) {
        actionLockedUntilMs = Math.max(actionLockedUntilMs, System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    private boolean ensureInternetForOnlineModes() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (NullUtil.isNull(cm)) {
            showUiToastDedupedStyled(getString(R.string.error_online_requires_internet), getString(R.string.fa_wifi), 0xFF6EE7FF);
            return false;
        }

        Network active = cm.getActiveNetwork();
        if (NullUtil.isNull(active)) {
            showUiToastDedupedStyled(getString(R.string.error_online_requires_internet), getString(R.string.fa_wifi), 0xFF6EE7FF);
            return false;
        }

        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        boolean connected = !NullUtil.isNull(caps) && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));

        if (!connected) {
            showUiToastDedupedStyled(getString(R.string.error_online_requires_internet), getString(R.string.fa_wifi), 0xFF6EE7FF);
        }

        return connected;
    }

    private void handleRoundFinished(String winnerSymbol, boolean draw) {
        if (matchManager.isOnlineMatch()) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
            if (draw) {
                victoryOverlayAnimator.showDraw(420);
            } else {
                victoryOverlayAnimator.showWin(winnerSymbol, 450);
            }
            return;
        }

        if (DomainSymmetries.X.getValue().equals(winnerSymbol)) roundsWonX++;
        if (DomainSymmetries.O.getValue().equals(winnerSymbol)) roundsWonO++;

        int winnerRounds = Math.max(roundsWonX, roundsWonO);
        int loserRounds = Math.min(roundsWonX, roundsWonO);

        if (winnerRounds >= ROUNDS_TO_WIN) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
            String champion = roundsWonX > roundsWonO ? DomainSymmetries.X.getValue() : DomainSymmetries.O.getValue();
            showUiToastDeduped(getString(R.string.rounds_finished_score, winnerRounds, loserRounds));
            victoryOverlayAnimator.showWin(champion, 450);
            return;
        }

        matchStarted = false;
        matchPhase = DomainMatchPhase.LOADING;
        String roundSummary = draw
                ? getString(R.string.round_draw_replay, roundsWonX, roundsWonO)
                : getString(R.string.round_result_score, winnerSymbol, roundsWonX, roundsWonO);
        showUiToastDeduped(roundSummary);

        handler.postDelayed(() -> {
            if (matchManager.isOnlineMatch()) return;
            gameManager.resetGame();
            victoryOverlayAnimator.clearLines();
            updateHeaderStatus();
            updateSkillVisuals();
            runCountdown(getLocalStartsLabel(), () -> beginPlayingAfterCountdown(false));
        }, 900L);
    }

    public void updateHeaderStatus() {
        updateTurnHud();
    }

    private void updateTurnHud() {
        String myName = getPlayerDisplayName();
        String rivalName = getOpponentDisplayName();

        boolean iAmX;
        if (passAndPlayMode) {
            iAmX = passPlayPlayerOneIsX;
        } else {
            iAmX = !matchManager.isOnlineMatch() || DomainSymmetries.X.getValue().equals(matchManager.getMySymbolOnline());
        }

        String xName = iAmX ? myName : rivalName;
        String oName = iAmX ? rivalName : myName;

        boolean online = matchManager.isOnlineMatch();
        boolean running = matchStarted && matchPhase == DomainMatchPhase.PLAYING && !gameManager.isGameOver() && !online;
        boolean xTurn = online
                ? DomainSymmetries.X.getValue().equals(matchManager.getTurnOnline())
                : state.isXTurn();

        turnHud.render(xName, oName, running, xTurn, iAmX);
    }

    private void updateSkillVisuals() {
        float triAlpha = state.canUseTriangle() && matchStarted ? 1f : 0.25f;
        float sqAlpha  = state.canUseSquare() && matchStarted ? 1f : 0.25f;

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

    private void startRematchIntro() {
        matchStarted = false;

        hideMatchmakingLoading();
        botManager.cancelPending();
        matchManager.stopOnlineBarAnim(true);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);

        applyArenaUiVisibility(true);

        binding.homeOverlay.setVisibility(View.GONE);
        binding.modeOverlay.setVisibility(View.GONE);

        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        matchIntroAnimator.startRematch();
    }

    private void startOfflineVsBot() {
        hideMatchmakingLoading();
        versusBot = true;
        opponentName = randomBotName();
        currentBotDifficulty = randomDifficulty();
        resetRoundSeries();

        setGameMode();
        state.setGameMode(DomainGameMode.BOT.getValue());
        gameManager.resetGame();
        boolean playerHomeVsBot = homeAwayManager.chooseHome(getPlayerDisplayName(), getString(R.string.label_bot));
        state.setXTurn(playerHomeVsBot);
        victoryOverlayAnimator.clearLines();
        applyArenaUiVisibility(false);

        updateHeaderStatus();
        updateSkillVisuals();

        matchIntroAnimator.startFromHome();
    }

    @NonNull
    private String buildTagFromUid(@NonNull String uid) {
        return uid.substring(0, Math.min(4, uid.length())).toUpperCase() + "#" + (1000 + (Math.abs(uid.hashCode()) % 9000));
    }

    private void applyArenaUiVisibility(boolean visible) {
        if (arenaVisibilityApplying) return;
        arenaVisibilityApplying = true;

        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        binding.turnHudBar.setVisibility(visibility);
        binding.containerTriangle.setVisibility(visibility);
        binding.lineLeftConnector.setVisibility(visibility);
        binding.boardContainer.setVisibility(visibility);
        binding.containerSquare.setVisibility(visibility);
        binding.lineRightConnector.setVisibility(visibility);

        arenaVisibilityApplying = false;
    }

    private void openProfileDialog() {
        String uid = getMyUidOrNull();
        if (NullUtil.isNull(uid)) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setText(getPlayerDisplayName());
        nameInput.setHint(getString(R.string.profile_name));
        styleInput(nameInput);

        String tag = buildTagFromUid(uid);

        AlertDialog profileDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_title))
                .setMessage(getString(R.string.profile_tag) + ": " + tag)
                .setView(nameInput)
                .setPositiveButton(getString(R.string.profile_save), (d, w) -> {
                    String displayName = NullUtil.isNull(nameInput.getText()) ? getPlayerDisplayName() : nameInput.getText().toString().trim();
                    if (displayName.isEmpty()) displayName = getPlayerDisplayName();
                    socialManager.upsertUserProfile(uid, displayName, tag);
                    playerServices.updateDisplayNameAndPersist(displayName);
                    updateHeaderStatus();
                })
                .setNegativeButton(getString(R.string.btn_back), null)
                .create();

        profileDialog.show();
        applyDialogStyle(profileDialog);
    }

    private void openFriendsDialog() {
        String uid = getMyUidOrNull();
        if (NullUtil.isNull(uid)) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        EditText tagInput = new EditText(this);
        tagInput.setHint(getString(R.string.friends_tag_hint));
        styleInput(tagInput);

        AlertDialog friendsDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.friends_title))
                .setMessage(getString(R.string.friends_add_by_tag))
                .setView(tagInput)
                .setPositiveButton(getString(R.string.btn_ok), (d, w) -> {
                    String tag = NullUtil.isNull(tagInput.getText()) ? "" : tagInput.getText().toString().trim().toUpperCase();
                    if (tag.isEmpty()) return;
                    socialManager.sendFriendRequestByTag(uid, tag, new SocialManager.Callback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(MainActivity.this, getString(R.string.friends_request_sent), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            Toast.makeText(MainActivity.this, getString(R.string.friends_request_error), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(getString(R.string.btn_back), null)
                .create();

        friendsDialog.show();
        applyDialogStyle(friendsDialog);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(matchmakingStatusTicker);
        botManager.cancelPending();
        matchManager.onDestroy();
        matchIntroAnimator.cancel();
        playerServices.onDestroy();
        String myUid = getMyUidOrNull();
        if (!NullUtil.isNull(myUid)) {
            socialManager.setPresence(myUid, "offline");
        }
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
