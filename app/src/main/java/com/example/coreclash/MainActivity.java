package com.example.coreclash;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
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

    private boolean localIntroCompleted = false;
    private boolean bothIntroReady = false;

    private static final long TURN_PROGRESS_DURATION_MS = 10000L;

    private String opponentName = "";
    private String selectedMode = DomainGameMode.CASUAL.getValue();
    private DomainDifficulty currentBotDifficulty = DomainDifficulty.BEGINNER;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private MatchManager matchManager;
    private BotManager botManager;
    private HomeAwayManager homeAwayManager;
    private SocialManager socialManager;

    private TimeoutBannerAnimator timeoutBannerAnimator;
    private MatchIntroAnimator matchIntroAnimator;
    private VictoryOverlayAnimator victoryOverlayAnimator;
    private static final int MAX_TIMEOUTS = 3;
    private boolean passPlayPlayerOneIsX = true;

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
        binding.btnProfile.setOnClickListener(v -> openProfileDialog());
        binding.btnFriends.setOnClickListener(v -> openFriendsDialog());

        playerServices = initPlayerServices();
        botManager = initBotManager();
        matchIntroAnimator = initMatchIntroAnimator();
        matchManager = initMatchManager();

        board.createBoard(this, binding.gridBoard, (row, col) -> {
            if (!matchStarted || gameManager.isGameOver()) {
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
        binding.btnMatchmakingCancel.setOnClickListener(v -> cancelMatchmakingSearch());

        playerServices.start();
        String myUid = getMyUidOrNull();
        if (myUid != null) {
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
                String uid = getMyUidOrNull();
                if (uid == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
                    return;
                }
                showMatchmakingLoading(getString(R.string.toast_looking_match));
                matchManager.startOnlineMatchmaking(() -> uid);
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
                String uid = getMyUidOrNull();
                if (uid == null) {
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
        if (passAndPlayMode) {
            return getString(R.string.label_player_two);
        }

        if (matchManager != null && matchManager.isOnlineMatch()) {
            String n = matchManager.getOpponentName();
            if (n != null && !n.trim().isEmpty()) {
                return n.trim();
            }
            return getString(R.string.status_waiting_opponent);
        }

        if (opponentName != null && !opponentName.trim().isEmpty()) {
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

                    @Override public void setArenaUiVisible(boolean visible) {
                        MainActivity.this.setArenaUiVisible(visible);
                    }

                    @Override public void onIntroFinished() {
                        localIntroCompleted = true;

                        if (matchManager.isOnlineMatch()) {
                            matchStarted = false;
                            matchPhase = DomainMatchPhase.LOADING;
                            bothIntroReady = false;
                            updateHeaderStatus();
                            updateSkillVisuals();

                            if (matchManager.getOnlineSession() != null) {
                                matchManager.getOnlineSession().markIntroReady(() -> runOnUiThread(() -> {
                                    bothIntroReady = true;

                                    matchManager.getOnlineSession().startPlayingWhenIntroFinished();

                                    runCountdown(getOnlineStartsLabel(), () -> {
                                        matchStarted = true;
                                        matchPhase = DomainMatchPhase.PLAYING;
                                        updateHeaderStatus();
                                        updateSkillVisuals();
                                        matchManager.onBothIntroReady();
                                    });
                                }));
                            }
                            return;
                        }

                        runCountdown(getLocalStartsLabel(), () -> {
                            matchStarted = true;
                            matchPhase = DomainMatchPhase.PLAYING;
                            updateHeaderStatus();
                            updateSkillVisuals();
                            botManager.maybeRunBotTurn();
                        });
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

                        showMatchmakingLoading(getString(R.string.status_waiting_opponent));
                        homeFlow.showWaitingOpponentUi();
                    }
                    @Override public void onRestoreMenuButtons() {
                        hideMatchmakingLoading();
                        homeFlow.restoreMenuButtons();
                    }
                    @Override public void onSetArenaUiVisible(boolean visible) {
                        MainActivity.this.setArenaUiVisible(visible);
                    }

                    @Override public void onBeforeOnlineMatchStart() {
                        versusBot = false;
                        passAndPlayMode = false;
                        matchPhase = DomainMatchPhase.LOADING;
                        localIntroCompleted = false;
                        bothIntroReady = false;

                        setGameMode();
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
                        matchStarted = false;
                        matchPhase = DomainMatchPhase.FINISHED;
                        victoryOverlayAnimator.showWin(winnerSymbol, 450);
                    }

                    @Override
                    public void onOnlineRemoteDraw() {
                        matchStarted = false;
                        matchPhase = DomainMatchPhase.FINISHED;
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


    private void configureMatchmakingOverlay() {
        Drawable icon = FontAwesomeIconFactory.createDrawable(this, getString(R.string.fa_xmark), 15, 0xFFEAF2FF);
        binding.btnMatchmakingCancel.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        binding.btnMatchmakingCancel.setCompoundDrawablePadding((int) (8 * getResources().getDisplayMetrics().density));
        binding.btnMatchmakingCancel.setEnabled(false);
    }

    private void setupMetaControls() {
        binding.btnRestart.setOnClickListener(v -> {
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

        binding.btnExit.setOnClickListener(v -> {
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

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        matchManager.sendMove(r, c);

        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
            victoryOverlayAnimator.showWin(symbol, 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
            victoryOverlayAnimator.showDraw(420);
        }
    }

    private void setGameMode() {
        String gameMode = selectedMode.equalsIgnoreCase(DomainGameMode.RANKED.getValue())
                ? DomainGameMode.RANKED.getValue()
                : DomainGameMode.CASUAL.getValue();
        state.setGameMode(gameMode);
    }


    private void runCountdown(@NonNull String startsLabel, @NonNull Runnable onFinish) {
        matchPhase = DomainMatchPhase.COUNTDOWN;
        binding.countdownOverlay.setVisibility(View.VISIBLE);
        binding.countdownOverlay.setAlpha(0f);
        binding.countdownOverlay.animate().alpha(1f).setDuration(120).start();
        binding.txtCountdownWhoStarts.setText(startsLabel);

        int[] ticks = {3, 2, 1};
        for (int i = 0; i < ticks.length; i++) {
            int value = ticks[i];
            long delay = i * 700L;
            handler.postDelayed(() -> binding.txtCountdownValue.setText(String.valueOf(value)), delay);
        }

        handler.postDelayed(() -> binding.txtCountdownValue.setText(getString(R.string.countdown_go)), 3 * 700L);
        handler.postDelayed(() -> {
            binding.countdownOverlay.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                binding.countdownOverlay.setVisibility(View.GONE);
                binding.countdownOverlay.setAlpha(1f);
                onFinish.run();
            }).start();
        }, 3 * 700L + 450L);
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

        setGameMode();
        state.setGameMode(DomainGameMode.LOCAL_PASS_PLAY.getValue());
        gameManager.resetGame();
        state.setXTurn(true);
        victoryOverlayAnimator.clearLines();
        setArenaUiVisible(false);

        updateHeaderStatus();
        updateSkillVisuals();
        matchIntroAnimator.startFromHome();
    }

    private void openLocalLobbyDialog() {
        String uid = getMyUidOrNull();
        if (uid == null) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
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
                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.local_lobby_enter_code))
                            .setView(input)
                            .setPositiveButton(getString(R.string.local_lobby_join), (d2, w2) -> {
                                String code = input.getText() == null ? "" : input.getText().toString().trim().toUpperCase();
                                if (code.length() < 4) {
                                    Toast.makeText(this, getString(R.string.error_invalid_room_code), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                showMatchmakingLoading(getString(R.string.local_lobby_waiting));
                                matchManager.joinLocalLobby(uid, code);
                            })
                            .setNegativeButton(getString(R.string.btn_back), null)
                            .show();
                })
                .show();
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

        if (!hadOnlineSession) {
            hideMatchmakingLoading();
            setArenaUiVisible(false);
            binding.homeOverlay.setVisibility(View.VISIBLE);
            binding.homeOverlay.setAlpha(1f);
            updateHeaderStatus();
            updateSkillVisuals();
        }

        Toast.makeText(this, getString(R.string.btn_cancel_matchmaking), Toast.LENGTH_SHORT).show();
    }

    private void showMatchmakingLoading(@NonNull String statusText) {
        binding.txtMatchmakingStatus.setText(statusText);
        binding.btnMatchmakingCancel.setEnabled(true);
        binding.matchmakingOverlay.setVisibility(View.VISIBLE);
        binding.matchmakingOverlay.setAlpha(0f);
        binding.matchmakingOverlay.animate().alpha(1f).setDuration(180).start();

        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        setArenaUiVisible(false);
    }

    private void hideMatchmakingLoading() {
        if (binding.matchmakingOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.matchmakingOverlay.animate()
                .alpha(0f)
                .setDuration(140)
                .withEndAction(() -> {
                    binding.matchmakingOverlay.setVisibility(View.GONE);
                    binding.btnMatchmakingCancel.setEnabled(false);
                    binding.matchmakingOverlay.setAlpha(1f);
                })
                .start();
    }

    private void showHomeScreen() {
        matchStarted = false;

        botManager.cancelPending();
        matchManager.stopOnlineBarAnim(true);
        victoryOverlayAnimator.hideInstant();
        victoryOverlayAnimator.clearLines();

        hideMatchmakingLoading();
        setArenaUiVisible(false);
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);
    }

    private String getPlayerDisplayName() {
        if (passAndPlayMode) {
            return getString(R.string.label_player_one);
        }

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

        if (beforeMoves == gameManager.getFinalMoves()) {
            return;
        }

        state.resetTimeoutStreak(actingX);
        updateHeaderStatus();
        updateSkillVisuals();

        if (won) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
            victoryOverlayAnimator.showWin(symbol, 450);
            return;
        }

        if (gameManager.isGameOver()) {
            matchStarted = false;
            matchPhase = DomainMatchPhase.FINISHED;
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

        setArenaUiVisible(true);

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

        setGameMode();
        state.setGameMode(DomainGameMode.BOT.getValue());
        gameManager.resetGame();
        boolean playerHomeVsBot = homeAwayManager.chooseHome(getPlayerDisplayName(), getString(R.string.label_bot));
        state.setXTurn(playerHomeVsBot);
        victoryOverlayAnimator.clearLines();
        setArenaUiVisible(false);

        updateHeaderStatus();
        updateSkillVisuals();

        matchIntroAnimator.startFromHome();
    }

    @NonNull
    private String buildTagFromUid(@NonNull String uid) {
        return uid.substring(0, Math.min(4, uid.length())).toUpperCase() + "#" + (1000 + (Math.abs(uid.hashCode()) % 9000));
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

    private void openProfileDialog() {
        String uid = getMyUidOrNull();
        if (uid == null) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setText(getPlayerDisplayName());
        nameInput.setHint(getString(R.string.profile_name));

        String tag = buildTagFromUid(uid);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_title))
                .setMessage(getString(R.string.profile_tag) + ": " + tag)
                .setView(nameInput)
                .setPositiveButton(getString(R.string.profile_save), (d, w) -> {
                    String displayName = nameInput.getText() == null ? getPlayerDisplayName() : nameInput.getText().toString().trim();
                    if (displayName.isEmpty()) {
                        displayName = getPlayerDisplayName();
                    }
                    socialManager.upsertUserProfile(uid, displayName, tag);
                    playerServices.updateDisplayNameAndPersist(displayName);
                    updateHeaderStatus();
                })
                .setNegativeButton(getString(R.string.btn_back), null)
                .show();
    }

    private void openFriendsDialog() {
        String uid = getMyUidOrNull();
        if (uid == null) {
            Toast.makeText(this, getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        EditText tagInput = new EditText(this);
        tagInput.setHint(getString(R.string.friends_tag_hint));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.friends_title))
                .setMessage(getString(R.string.friends_add_by_tag))
                .setView(tagInput)
                .setPositiveButton(getString(R.string.btn_ok), (d, w) -> {
                    String tag = tagInput.getText() == null ? "" : tagInput.getText().toString().trim().toUpperCase();
                    if (tag.isEmpty()) {
                        return;
                    }
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
                .show();
    }

    @Override
    protected void onDestroy() {
        botManager.cancelPending();
        matchManager.onDestroy();
        matchIntroAnimator.cancel();
        playerServices.onDestroy();
        String myUid = getMyUidOrNull();
        if (myUid != null) {
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

    private void updateAbandonHud() {
        turnHud.renderAbandon(this, state.getTimeoutStreakX(), state.getTimeoutStreakO(), MAX_TIMEOUTS);
    }
}
