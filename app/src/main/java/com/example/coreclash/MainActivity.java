package com.example.coreclash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;

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
import manager.MatchManager;
import manager.PlayerServicesManager;
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

    private StoreManager storeManager;
    private AuthenticationManager authenticationManager;
    private SettingManager settingManager;
    private TurnHudManager turnHud;

    private PlayerServicesManager playerServices;
    private HomeFlowManager homeFlow;

    private PlayerProfile currentProfile;

    private boolean matchStarted = false;
    private boolean versusBot = false;

    private boolean localIntroCompleted = false;
    private boolean bothIntroReady = false;

    private static final long TURN_PROGRESS_DURATION_MS = 10000L;

    private String opponentName = "";
    private String selectedMode = DomainGameMode.CASUAL.getValue();
    private DomainDifficulty currentBotDifficulty = DomainDifficulty.BEGINNER;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private MatchManager matchManager;
    private BotManager botManager;

    private TimeoutBannerAnimator timeoutBannerAnimator;
    private MatchIntroAnimator matchIntroAnimator;
    private VictoryOverlayAnimator victoryOverlayAnimator;
    private static final int MAX_TIMEOUTS = 3;

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

        turnHud = initTurnHudManager();

        homeFlow = initHomeFlow();
        homeFlow.setSelectedMatchKind(enums.DomainMatchKind.OFFLINE_BOT);
        homeFlow.bind();

        playerServices = initPlayerServices();
        botManager = initBotManager();
        matchIntroAnimator = initMatchIntroAnimator();
        matchManager = initMatchManager();

        board.createBoard(this, binding.gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) return;

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

            if (versusBot && !state.isXTurn()) return;

            playTurn(row, col);
        });

        setupSkills();
        setupMetaControls();

        playerServices.start();

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
                state,
                new BotManager.Gate() {
                    @Override public boolean isOnlineMatch() { return matchManager != null && matchManager.isOnlineMatch(); }
                    @Override public boolean isVersusBot() { return versusBot; }
                    @Override public boolean isMatchStarted() { return matchStarted; }
                    @Override public boolean isXTurn() { return state.isXTurn(); }
                    @Override public boolean isGameOver() { return gameManager.isGameOver(); }
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
            @Override public void onPlayOnlineClicked() {
                startOfflineVsBot();
            }

            @Override public void onStoreClicked() {
                if (storeManager == null) {
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
                matchManager.startOnlineMatchmaking(MainActivity.this::getMyUidOrNull);
            }

            @Override public void onConfirmLocalMultiplayer() {
                Toast.makeText(MainActivity.this, "Local multiplayer is not available yet.", Toast.LENGTH_SHORT).show();
            }

            @Override public void onModeChanged(@NonNull enums.DomainMatchKind selected) {

            }
        });
    }

    private PlayerServicesManager initPlayerServices() {
        return new PlayerServicesManager(
                this,
                r -> runOnUiThread(r),
                binding,
                board,
                handler,
                new PlayerServicesManager.Callbacks() {
                    @Override public void onProfileReady(@NonNull PlayerProfile profile) { currentProfile = profile; }
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
        if (matchManager != null && matchManager.isOnlineMatch()) {
            String n = matchManager.getOpponentName();
            if (n != null && !n.trim().isEmpty()) return n.trim();
            return getString(R.string.status_waiting_opponent);
        }

        if (opponentName != null && !opponentName.trim().isEmpty()) return opponentName.trim();
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

                    @Override public void setArenaUiVisible(boolean visible) {
                        MainActivity.this.setArenaUiVisible(visible);
                    }

                    @Override public void onIntroFinished() {
                        localIntroCompleted = true;

                        if (matchManager.isOnlineMatch()) {
                            matchStarted = false;
                            bothIntroReady = false;
                            updateHeaderStatus();
                            updateSkillVisuals();

                            if (matchManager.getOnlineSession() != null) {
                                matchManager.getOnlineSession().markIntroReady(() -> runOnUiThread(() -> {
                                    bothIntroReady = true;

                                    matchManager.getOnlineSession().startPlayingWhenIntroFinished();

                                    matchStarted = true;
                                    updateHeaderStatus();
                                    updateSkillVisuals();
                                    matchManager.startOrUpdateOnlineBar();
                                    matchManager.scheduleOnlineTimeoutBanner();
                                }));
                            }
                            return;
                        }

                        matchStarted = true;
                        updateHeaderStatus();
                        updateSkillVisuals();
                        botManager.maybeRunBotTurn();
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
                state,
                new MatchManager.Callbacks() {
                    @Override public void runOnUi(@NonNull Runnable r) { runOnUiThread(r); }
                    @Override public void onUpdateHeaderStatus() { updateHeaderStatus(); }
                    @Override public void onUpdateSkillVisuals() { updateSkillVisuals(); }
                    @Override public void onShowWaitingOpponentUi() {
                        matchStarted = false;
                        bothIntroReady = false;
                        localIntroCompleted = false;

                        opponentName = getString(R.string.status_waiting_opponent);
                        updateHeaderStatus();

                        homeFlow.showWaitingOpponentUi();
                    }
                    @Override public void onRestoreMenuButtons() { homeFlow.restoreMenuButtons(); }
                    @Override public void onSetArenaUiVisible(boolean visible) {
                        MainActivity.this.setArenaUiVisible(visible);
                    }

                    @Override public void onBeforeOnlineMatchStart() {
                        versusBot = false;
                        localIntroCompleted = false;
                        bothIntroReady = false;

                        setGameMode();
                        gameManager.resetGame();
                        victoryOverlayAnimator.clearLines();

                        opponentName = matchManager.getOpponentName();
                    }

                    @Override public void onPlayTimeoutBanner(boolean xSide) { timeoutBannerAnimator.play(xSide); }
                    @Override public void onHideTimeoutBanner(boolean xSide) { timeoutBannerAnimator.hide(xSide); }

                    @Override public boolean isBothIntroReady() { return bothIntroReady; }

                    @Override public void onOnlineMatchShouldStartPlaying() {
                        matchIntroAnimator.startFromHome();
                    }

                    @Override
                    public void onOnlineRemoteWin(@NonNull String winnerSymbol) {
                        matchStarted = false;
                        victoryOverlayAnimator.showWin(winnerSymbol, 450);
                    }

                    @Override
                    public void onOnlineRemoteDraw() {
                        matchStarted = false;
                        victoryOverlayAnimator.showDraw(420);
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
                    if (matchManager.isOnlineMatch()) return;
                    if (!matchStarted || gameManager.isGameOver()) return;
                    if (state.isXTurn() != xTurnStarted) return;

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
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
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

            boolean actingX = state.isXTurn();

            if (gameManager.useTriangle()) {
                state.resetTimeoutStreak(actingX);

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

            boolean actingX = state.isXTurn();

            if (gameManager.useSquare()) {
                state.resetTimeoutStreak(actingX);

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

    private void setupMetaControls() {
        binding.btnRestart.setOnClickListener(v -> {
            victoryOverlayAnimator.hide();
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

        binding.btnExit.setOnClickListener(v -> {
            victoryOverlayAnimator.hide();
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
        localIntroCompleted = false;
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

        if (beforeMoves == gameManager.getFinalMoves()) return;

        matchManager.sendMove(r, c);

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            victoryOverlayAnimator.showWin(symbol, 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            victoryOverlayAnimator.showDraw(420);
        }
    }

    private void setGameMode() {
        String gameMode = selectedMode.equalsIgnoreCase(DomainGameMode.RANKED.getValue())
                ? DomainGameMode.RANKED.getValue()
                : DomainGameMode.CASUAL.getValue();
        state.setGameMode(gameMode);
    }

    private void showHomeScreen() {
        matchStarted = false;

        botManager.cancelPending();
        matchManager.stopOnlineBarAnim(true);
        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        setArenaUiVisible(true);
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);
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

    private String getMyUidOrNull() {
        var u = FirebaseAuth.getInstance().getCurrentUser();
        return u == null ? null : u.getUid();
    }

    private void playTurn(int row, int col) {
        boolean actingX = state.isXTurn();
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(row, col);

        if (beforeMoves == gameManager.getFinalMoves()) return;

        state.resetTimeoutStreak(actingX);
        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            victoryOverlayAnimator.showWin(symbol, 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            victoryOverlayAnimator.showDraw(420);
            return;
        }

        botManager.maybeRunBotTurn();
    }

    public void updateHeaderStatus() {
        updateTurnHud();
    }

    private void updateTurnHud() {
        String myName = getPlayerDisplayName();
        String rivalName = getOpponentDisplayName();

        boolean iAmX = !matchManager.isOnlineMatch() || DomainSymmetries.X.getValue().equals(matchManager.getMySymbolOnline());

        String xName = iAmX ? myName : rivalName;
        String oName = iAmX ? rivalName : myName;

        String youLabel = getString(R.string.hud_you_suffix, getString(R.string.hud_you_label));
        String xYouSuffix = iAmX ? youLabel : "";
        String oYouSuffix = iAmX ? "" : youLabel;

        xName = getString(R.string.hud_player_with_symbol_tag, xName, DomainSymmetries.X.getValue(), xYouSuffix);
        oName = getString(R.string.hud_player_with_symbol_tag, oName, DomainSymmetries.O.getValue(), oYouSuffix);

        boolean running = matchStarted && !gameManager.isGameOver();
        boolean xTurn = matchManager.isOnlineMatch() ? DomainSymmetries.X.getValue().equals(matchManager.getTurnOnline()) : state.isXTurn();

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

        botManager.cancelPending();
        matchManager.stopOnlineBarAnim(true);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);

        setArenaUiVisible(true);

        binding.homeOverlay.setVisibility(View.GONE);
        binding.modeOverlay.setVisibility(View.GONE);

        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        matchIntroAnimator.startRematch();
    }

    private void startOfflineVsBot() {
        versusBot = true;
        opponentName = randomBotName();
        currentBotDifficulty = randomDifficulty();

        setGameMode();
        gameManager.resetGame();
        victoryOverlayAnimator.clearLines();
        setArenaUiVisible(false);

        updateHeaderStatus();
        updateSkillVisuals();

        matchIntroAnimator.startFromHome();
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

    @Override
    protected void onDestroy() {
        botManager.cancelPending();
        matchManager.onDestroy();
        matchIntroAnimator.cancel();
        playerServices.onDestroy();
        super.onDestroy();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void updateAbandonHud() {
        turnHud.renderAbandon(this, state.getTimeoutStreakX(), state.getTimeoutStreakO(), MAX_TIMEOUTS);
    }
}
