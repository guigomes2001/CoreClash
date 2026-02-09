package com.example.coreclash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
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

import enums.DomainBotNames;
import enums.DomainDifficulty;
import enums.DomainGameMode;
import enums.DomainMatchStatus; // ok se você tiver esse enum; se não usar pode remover
import enums.DomainSymbols;
import game.GameState;
import game.OnlineMatchSession;
import game.OnlineMatchmaking;
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
    private AuthenticationManager authenticationManager;
    private SettingManager settingManager;
    private TurnHudManager turnHud;

    private PlayerProfile currentProfile;

    private boolean matchStarted = false;
    private boolean versusBot = false;

    private boolean isOnlineMatch = false;
    private boolean iAmXOnline = true;

    private Boolean lastTurnProgressIsX = null;
    private static final long TURN_PROGRESS_DURATION_MS = 10000L;

    private String opponentName = "";
    private String selectedMode = DomainGameMode.CASUAL.getValue();
    private enums.DomainMatchKind selectedMatchKind = enums.DomainMatchKind.OFFLINE_BOT;
    private DomainDifficulty currentBotDifficulty = DomainDifficulty.BEGINNER;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    private OnlineMatchmaking matchmaking;
    private OnlineMatchSession onlineSession;
    private String mySymbolOnline = "X";
    private String turnOnline = "X";
    private long turnStartedAtOnlineMs = 0L;
    private long turnDurationOnlineMs = TURN_PROGRESS_DURATION_MS;
    private ValueAnimator onlineBarAnim;
    private AnimatorSet timeoutBannerAnimX;
    private AnimatorSet timeoutBannerAnimO;
    private String scheduledTimeoutTurnKey = "";
    private String lastTimeoutBannerTurnKey = "";
    private boolean localIntroCompleted = false;
    private boolean bothIntroReady = false;

    private final Runnable onlineTimeoutBannerRunnable = this::maybeShowOnlineTimeoutBanner;

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
            if (!matchStarted || gameManager.isGameOver()) return;

            if (isOnlineMatch) {
                // 🔒 trava clique até os dois concluírem a intro
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!isMyTurnOnline()) {
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
        setupHomeFlow();
        initPlayerServices();
        setupMetaControls();

        matchmaking = new OnlineMatchmaking();
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
                    if (isOnlineMatch) return;
                    if (!matchStarted || gameManager.isGameOver()) return;
                    if (state.isXTurn() != xTurnStarted) return;

                    playTimeoutBanner(xTurnStarted);
                    Toast.makeText(MainActivity.this, getString(R.string.toast_turn_passed), Toast.LENGTH_SHORT).show();
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
                            @Override public void onGoogleLinked(String displayName) {
                                if (currentProfile != null) {
                                    currentProfile.displayName = displayName;
                                    profileManager.persistProfile();
                                }
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

    private void setupHomeFlow() {
        binding.btnPlay.setOnClickListener(v -> openModeModal());
        binding.btnOnline.setOnClickListener(v -> startOnlineMatchmaking());

        binding.btnStore.setOnClickListener(v -> storeManager.openStore());
        binding.btnSettings.setOnClickListener(v -> settingManager.openSettings());

        binding.btnModeOffline.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.OFFLINE_BOT;
            updateModeButtonStyles();
        });

        binding.btnModeOnline.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.ONLINE_PVP;
            updateModeButtonStyles();
        });

        binding.btnModeCancel.setOnClickListener(v -> closeModeModal());
        binding.btnModeConfirm.setOnClickListener(v -> {
            closeModeModal();

            if (selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP) {
                startOnlineMatchmaking();
            } else if (selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT) {
                startOfflineVsBot();
            } else {
                Toast.makeText(this, "Local multiplayer is not available yet.", Toast.LENGTH_SHORT).show();
            }
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

            if (isOnlineMatch) {
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!isMyTurnOnline()) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
            }

            if (gameManager.useTriangle()) {
                AnimationHelper.spin(v);

                if (isOnlineMatch && onlineSession != null) {
                    onlineSession.sendTriangle();
                }

                updateHeaderStatus();
                updateSkillVisuals();

                if (!isOnlineMatch) maybeRunBotTurn();
            }
        });

        binding.containerSquare.setOnClickListener(v -> {
            if (!matchStarted || gameManager.isGameOver() || !state.canUseSquare()) {
                AnimationHelper.shakeButton(v);
                return;
            }

            if (isOnlineMatch) {
                if (!bothIntroReady) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
                if (!isMyTurnOnline()) {
                    AnimationHelper.shakeButton(binding.turnHudBar);
                    return;
                }
            }

            if (gameManager.useSquare()) {
                AnimationHelper.pulse(v);

                if (isOnlineMatch && onlineSession != null) {
                    onlineSession.sendSquare();
                }

                updateHeaderStatus();
                updateSkillVisuals();

                if (!isOnlineMatch) maybeRunBotTurn();
            }
        });
    }

    private void setupMetaControls() {
        binding.btnRestart.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            binding.victoryLineView.clear();
            updateSkillVisuals();

            if (isOnlineMatch) {
                endOnlineSessionToMenu();
                return;
            }

            startRematchIntro();
        });

        binding.btnExit.setOnClickListener(v -> {
            hideVictoryScreen();
            gameManager.resetGame();
            binding.victoryLineView.clear();

            if (isOnlineMatch) {
                endOnlineSessionToMenu();
                return;
            }

            showHomeScreen();
            updateHeaderStatus();
            updateSkillVisuals();
        });
    }

    private void startOnlineMatchmaking() {
        String myUid = getMyUidOrNull();
        if (myUid == null) {
            Toast.makeText(this, "Authentication is not ready yet. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, getString(R.string.toast_looking_match), Toast.LENGTH_SHORT).show();
        matchmaking.cleanupOldWaitingRooms();

        matchmaking.findOrCreateMatch(myUid, new OnlineMatchmaking.MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
                isOnlineMatch = true;
                versusBot = false;
                iAmXOnline = iAmX;
                mySymbolOnline = iAmXOnline ? "X" : "O";

                opponentName = opponentUid.trim().isEmpty()
                        ? getString(R.string.status_waiting_opponent)
                        : "Player " + opponentUid.substring(0, Math.min(6, opponentUid.length()));

                onlineSession = new OnlineMatchSession(roomId, myUid, mySymbolOnline);
                localIntroCompleted = false;
                bothIntroReady = false;

                hookOnlineListeners();

                setGameMode();
                gameManager.resetGame();
                binding.victoryLineView.clear();
                setArenaUiVisible(false);

                updateHeaderStatus();
                updateSkillVisuals();

                if (iAmXOnline && opponentUid.trim().isEmpty()) {
                    showWaitingOpponentUi();
                }

                onlineSession.listenOpponentJoin(oUid -> runOnUiThread(() -> {
                    opponentName = "Player " + oUid.substring(0, Math.min(6, oUid.length()));
                    updateHeaderStatus();

                    if (iAmXOnline) {
                        onlineSession.scheduleIntroIfHost(true, 500L, 3000L);
                    }
                }));

                onlineSession.listenIntroClock((startAt, durationMs, serverNow) -> {
                    long delay = Math.max(0L, startAt - serverNow);
                    runOnUiThread(() -> handler.postDelayed(() -> {
                        if (!isOnlineMatch || onlineSession == null) return;
                        startMatchIntro();
                    }, delay));
                });
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWaitingOpponentUi() {
        matchStarted = false;
        bothIntroReady = false;
        localIntroCompleted = false;

        opponentName = getString(R.string.status_waiting_opponent);
        updateHeaderStatus();

        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);

        binding.btnPlay.setEnabled(false);
        binding.btnOnline.setEnabled(false);
    }

    private void restoreMenuButtons() {
        binding.btnPlay.setEnabled(true);
        binding.btnOnline.setEnabled(true);
    }

    private void hookOnlineListeners() {
        if (onlineSession == null) return;

        onlineSession.startListening(
                new OnlineMatchSession.ActionListener() {
                    @Override
                    public void onRemoteMove(int r, int c, @NonNull String byUid) {
                        runOnUiThread(() -> playTurnRemote(r, c));
                    }

                    @Override
                    public void onRemoteTriangle(@NonNull String byUid) {
                        runOnUiThread(() -> {
                            gameManager.useTriangle();
                            updateHeaderStatus();
                            updateSkillVisuals();
                        });
                    }

                    @Override
                    public void onRemoteSquare(@NonNull String byUid) {
                        runOnUiThread(() -> {
                            gameManager.useSquare();
                            updateHeaderStatus();
                            updateSkillVisuals();
                        });
                    }

                    @Override
                    public void onOpponentLeft() {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, getString(R.string.toast_opponent_left), Toast.LENGTH_SHORT).show();
                            endOnlineSessionToMenu();
                        });
                    }
                },
                (turn, turnStartedAtMs, turnDurationMs, serverNowApproxMs) -> {
                    turnOnline = turn;
                    turnStartedAtOnlineMs = turnStartedAtMs;
                    turnDurationOnlineMs = turnDurationMs;

                    runOnUiThread(() -> {
                        updateHeaderStatus();
                        if (bothIntroReady) {
                            startOrUpdateOnlineBar();
                            scheduleOnlineTimeoutBanner();
                        } else {
                            stopOnlineBarAnim();
                        }
                    });
                }
        );
    }

    private void startOrUpdateOnlineBar() {
        stopOnlineBarAnim();

        long nowServer = (onlineSession != null) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long elapsed = Math.max(0L, nowServer - turnStartedAtOnlineMs);
        long remaining = Math.max(0L, turnDurationOnlineMs - elapsed);

        int startProgress = (int) (100f * (remaining / (float) turnDurationOnlineMs));

        final boolean xTurn = "X".equals(turnOnline);
        final android.widget.ProgressBar active = xTurn ? binding.progressTurnHudX : binding.progressTurnHudO;
        final android.widget.ProgressBar inactive = xTurn ? binding.progressTurnHudO : binding.progressTurnHudX;

        inactive.setProgress(0);
        active.setProgress(startProgress);

        onlineBarAnim = ValueAnimator.ofInt(startProgress, 0);
        onlineBarAnim.setDuration(remaining);
        onlineBarAnim.setInterpolator(new LinearInterpolator());
        onlineBarAnim.addUpdateListener(a -> active.setProgress((int) a.getAnimatedValue()));
        onlineBarAnim.start();
    }

    private void stopOnlineBarAnim() {
        if (onlineBarAnim != null) {
            onlineBarAnim.cancel();
            onlineBarAnim = null;
        }
        handler.removeCallbacks(onlineTimeoutBannerRunnable);
        scheduledTimeoutTurnKey = "";
        hideTimeoutBanner(true);
        hideTimeoutBanner(false);
    }

    private void scheduleOnlineTimeoutBanner() {
        if (!isOnlineMatch) return;

        handler.removeCallbacks(onlineTimeoutBannerRunnable);

        long nowServer = (onlineSession != null) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long endAt = turnStartedAtOnlineMs + turnDurationOnlineMs;
        long delay = Math.max(0L, endAt - nowServer) + 24L;

        scheduledTimeoutTurnKey = turnOnline + ":" + turnStartedAtOnlineMs;
        handler.postDelayed(onlineTimeoutBannerRunnable, delay);
    }

    private void maybeShowOnlineTimeoutBanner() {
        if (!isOnlineMatch) return;

        String currentTurnKey = turnOnline + ":" + turnStartedAtOnlineMs;
        if (!currentTurnKey.equals(scheduledTimeoutTurnKey)) return;

        long nowServer = (onlineSession != null) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long endAt = turnStartedAtOnlineMs + turnDurationOnlineMs;

        if (nowServer < endAt) {
            long extra = Math.max(8L, endAt - nowServer);
            handler.postDelayed(onlineTimeoutBannerRunnable, extra);
            return;
        }

        if (currentTurnKey.equals(lastTimeoutBannerTurnKey)) return;

        lastTimeoutBannerTurnKey = currentTurnKey;
        boolean timedOutX = "X".equals(turnOnline);
        playTimeoutBanner(timedOutX);
        Toast.makeText(MainActivity.this, getString(R.string.toast_turn_passed), Toast.LENGTH_SHORT).show();

        if (onlineSession != null) {
            String expiredTurn = turnOnline;
            onlineSession.advanceTurnIfExpired(expiredTurn, advanced -> {
                if (!advanced) Log.d("RTDB", "Timeout turn advance skipped or already advanced.");
            });
        }
    }

    private void playTimeoutBanner(boolean xSide) {
        final android.widget.FrameLayout track = xSide ? binding.turnHudTrackX : binding.turnHudTrackO;
        final android.widget.TextView arrow1   = xSide ? binding.txtTimeoutArrow1X : binding.txtTimeoutArrow1O;
        final android.widget.TextView arrow2   = xSide ? binding.txtTimeoutArrow2X : binding.txtTimeoutArrow2O;
        final android.widget.TextView arrow3   = xSide ? binding.txtTimeoutArrow3X : binding.txtTimeoutArrow3O;
        final android.widget.TextView label    = xSide ? binding.txtTimeoutX : binding.txtTimeoutO;

        stopTimeoutBannerAnimation(xSide);

        track.post(() -> {
            int trackWidth = track.getWidth();

            label.setVisibility(View.VISIBLE);

            if (label.getWidth() <= 0) {
                label.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
            }

            int labelWidth = label.getWidth() > 0 ? label.getWidth() : label.getMeasuredWidth();

            if (trackWidth <= 0) {
                track.measure(
                        View.MeasureSpec.makeMeasureSpec(track.getMeasuredWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(track.getMeasuredHeight(), View.MeasureSpec.AT_MOST)
                );
                trackWidth = track.getWidth() > 0 ? track.getWidth() : track.getMeasuredWidth();
            }

            if (trackWidth <= 0 || labelWidth <= 0) {
                label.setAlpha(1f);
                label.setTranslationX(0f);
                label.animate()
                        .alpha(0f)
                        .setStartDelay(650)
                        .setDuration(350)
                        .withEndAction(() -> resetTimeoutElement(label, 0f))
                        .start();
                return;
            }

            float startX  = -labelWidth - 30f;
            float centerX = (trackWidth - labelWidth) / 2f;
            float endX    = trackWidth + 28f;

            // setas rápidas em cascata para dar sensação de avanço de turno
            playTimeoutArrow(arrow1, startX - 36f, centerX - 54f, centerX - 20f, 0,   210, 90);
            playTimeoutArrow(arrow2, startX - 18f, centerX - 28f, centerX + 6f,  70,  220, 95);
            playTimeoutArrow(arrow3, startX + 2f,  centerX - 4f,  centerX + 30f, 140, 230, 100);

            label.setTranslationX(startX);
            label.setAlpha(0f);

            ObjectAnimator alphaIn = ObjectAnimator.ofFloat(label, View.ALPHA, 0f, 1f);
            alphaIn.setStartDelay(90);
            alphaIn.setDuration(170);

            ObjectAnimator fastIn = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, startX, centerX - 10f);
            fastIn.setDuration(540);
            fastIn.setInterpolator(new DecelerateInterpolator(1.35f));

            ObjectAnimator slowCenter = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, centerX - 10f, centerX + 12f);
            slowCenter.setDuration(1450);
            slowCenter.setInterpolator(new LinearInterpolator());

            ObjectAnimator fastOut = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, centerX + 12f, endX);
            fastOut.setDuration(460);
            fastOut.setInterpolator(new AccelerateInterpolator(1.65f));

            ObjectAnimator alphaOut = ObjectAnimator.ofFloat(label, View.ALPHA, 1f, 0f);
            alphaOut.setStartDelay(1760);
            alphaOut.setDuration(420);

            AnimatorSet labelMotion = new AnimatorSet();
            labelMotion.playSequentially(fastIn, slowCenter, fastOut);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(labelMotion, alphaIn, alphaOut);
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { resetTimeoutElement(label, startX); }
                @Override public void onAnimationCancel(Animator animation) { resetTimeoutElement(label, startX); }
            });
            set.start();

            if (xSide) timeoutBannerAnimX = set;
            else timeoutBannerAnimO = set;
        });
    }

    private void playTimeoutArrow(@NonNull android.widget.TextView arrow, float startX, float centerX, float endX,
                                  long startDelay, long moveDuration, long fadeOutDuration) {
        resetTimeoutElement(arrow, startX);
        arrow.setVisibility(View.VISIBLE);

        ObjectAnimator alphaIn = ObjectAnimator.ofFloat(arrow, View.ALPHA, 0f, 1f);
        alphaIn.setStartDelay(startDelay);
        alphaIn.setDuration(60);

        ObjectAnimator move = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_X, startX, centerX, endX);
        move.setStartDelay(startDelay);
        move.setDuration(moveDuration);
        move.setInterpolator(new AccelerateInterpolator(1.8f));

        ObjectAnimator alphaOut = ObjectAnimator.ofFloat(arrow, View.ALPHA, 1f, 0f);
        alphaOut.setStartDelay(startDelay + Math.max(120L, moveDuration - 40L));
        alphaOut.setDuration(fadeOutDuration);

        alphaIn.start();
        move.start();
        alphaOut.start();
    }

    private void resetTimeoutElement(@NonNull android.widget.TextView view, float startX) {
        view.setTranslationX(startX);
        view.setAlpha(0f);
        view.setVisibility(View.INVISIBLE);
    }

    private void hideTimeoutBanner(boolean xSide) {
        stopTimeoutBannerAnimation(xSide);

        final android.widget.TextView arrow1 = xSide ? binding.txtTimeoutArrow1X : binding.txtTimeoutArrow1O;
        final android.widget.TextView arrow2 = xSide ? binding.txtTimeoutArrow2X : binding.txtTimeoutArrow2O;
        final android.widget.TextView arrow3 = xSide ? binding.txtTimeoutArrow3X : binding.txtTimeoutArrow3O;
        final android.widget.TextView label = xSide ? binding.txtTimeoutX : binding.txtTimeoutO;

        arrow1.animate().cancel();
        arrow2.animate().cancel();
        arrow3.animate().cancel();
        label.animate().cancel();

        arrow1.setVisibility(View.INVISIBLE);
        arrow2.setVisibility(View.INVISIBLE);
        arrow3.setVisibility(View.INVISIBLE);
        label.setVisibility(View.INVISIBLE);

        arrow1.setAlpha(0f);
        arrow2.setAlpha(0f);
        arrow3.setAlpha(0f);
        label.setAlpha(0f);
    }

    private void stopTimeoutBannerAnimation(boolean xSide) {
        AnimatorSet set = xSide ? timeoutBannerAnimX : timeoutBannerAnimO;
        if (set != null) set.cancel();
        if (xSide) timeoutBannerAnimX = null;
        else timeoutBannerAnimO = null;
    }

    private void endOnlineSessionToMenu() {
        stopOnlineBarAnim();
        try {
            if (onlineSession != null) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}

        onlineSession = null;
        isOnlineMatch = false;
        versusBot = false;
        localIntroCompleted = false;
        bothIntroReady = false;
        lastTimeoutBannerTurnKey = "";

        showHomeScreen();
        updateHeaderStatus();
        updateSkillVisuals();
    }

    private boolean isMyTurnOnline() {
        return mySymbolOnline.equals(turnOnline);
    }

    private void playTurnLocalAndSend(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(r, c);

        if (beforeMoves == gameManager.getFinalMoves()) return;

        if (onlineSession != null) {
            onlineSession.sendMove(r, c);
        }

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
        }
    }

    private void playTurnRemote(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        String symbol = gameManager.getCurrentPlayerSymbol();
        boolean won = gameManager.play(r, c);

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

        handler.removeCallbacks(onlineTimeoutBannerRunnable);
        stopOnlineBarAnim();

        lastTurnProgressIsX = null;

        setArenaUiVisible(true);
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);
        binding.versusOverlay.setVisibility(View.GONE);

        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);
    }


    private void startMatchIntro() {
        restoreMenuButtons();
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
        resetVersusUiState();

        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(1f);

        setArenaUiVisible(true);

        String playerName = getPlayerDisplayName();

        binding.txtVersusX.setText(playerName);
        binding.txtVersusO.setText(opponentName);

        String modeLabel = selectedMode.equalsIgnoreCase(DomainGameMode.RANKED.getValue())
                ? getString(R.string.mode_ranked_label)
                : getString(R.string.mode_casual_label);
        binding.txtVersusMode.setText(modeLabel);

        binding.txtVersusCenter.setText(getString(R.string.versus_battle_title));

        binding.txtVersusMode.setAlpha(0f);
        binding.viewVersusStripeTop.setAlpha(0f);
        binding.viewVersusStripeBottom.setAlpha(0f);

        binding.txtBreakX.setAlpha(0f);
        binding.txtBreakO.setAlpha(0f);
        binding.txtBreakX.setTranslationX(0f);
        binding.txtBreakO.setTranslationX(0f);

        binding.txtVersusMode.animate().alpha(1f).setDuration(220).start();
        binding.viewVersusStripeTop.animate().alpha(1f).setDuration(220).start();
        binding.viewVersusStripeBottom.animate().alpha(1f).setDuration(220).start();

        handler.postDelayed(this::playVersusBreakAnimation, 2500);
    }

    private void playVersusBreakAnimation() {
        if (binding.versusOverlay.getVisibility() != View.VISIBLE) return;

        binding.txtBreakX.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        binding.txtBreakO.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();

        float dist = binding.getRoot().getWidth() * 0.65f;

        binding.txtBreakX.animate()
                .translationX(-dist)
                .alpha(0f)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        binding.txtBreakO.animate()
                .translationX(dist)
                .alpha(0f)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        binding.versusBandRoot.animate()
                .alpha(0f)
                .setDuration(260)
                .start();

        binding.versusDim.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction(() -> {
                    binding.versusOverlay.setVisibility(View.GONE);

                    localIntroCompleted = true;

                    if (isOnlineMatch) {
                        matchStarted = false;
                        bothIntroReady = false;
                        updateHeaderStatus();
                        updateSkillVisuals();

                        if (onlineSession != null) {
                            onlineSession.markIntroReady(() -> runOnUiThread(() -> {
                                bothIntroReady = true;

                                onlineSession.startPlayingWhenIntroFinished();

                                matchStarted = true;
                                updateHeaderStatus();
                                updateSkillVisuals();
                                startOrUpdateOnlineBar();
                                scheduleOnlineTimeoutBanner();
                            }));
                        }
                        return;
                    }

                    matchStarted = true;
                    updateHeaderStatus();
                    updateSkillVisuals();
                    maybeRunBotTurn();
                })
                .start();
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
        if (isOnlineMatch) return;
        if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) return;

        long thinkDelayMs = 900L + random.nextInt(700);
        handler.postDelayed(() -> {
            if (isOnlineMatch) return;
            if (!versusBot || !matchStarted || state.isXTurn() || gameManager.isGameOver()) return;

            if (shouldBotUseSkill() && tryUseRandomBotSkill()) {
                updateHeaderStatus();
                updateSkillVisuals();
                return;
            }

            int[] move = chooseBotMove(currentBotDifficulty);
            if (move != null) playTurn(move[0], move[1]);
        }, thinkDelayMs);
    }

    private boolean shouldBotUseSkill() {
        double chance = switch (currentBotDifficulty) {
            case BEGINNER -> 0.20;
            case MODERATE -> 0.55;
            case GAME_MASTER -> 0.80;
        };
        return random.nextDouble() < chance;
    }

    private boolean tryUseRandomBotSkill() {
        boolean canTriangle = gameManager.canUseTriangleNow();
        boolean canSquare = gameManager.canUseSquareNow();
        if (!canTriangle && !canSquare) return false;

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
        if (moves.isEmpty()) return null;

        if (difficulty == DomainDifficulty.BEGINNER) {
            return moves.get(random.nextInt(moves.size()));
        }

        int[] win = gameManager.findWinningMoveFor(DomainSymbols.O.getValue());
        if (win != null) return win;

        int[] block = gameManager.findWinningMoveFor(DomainSymbols.X.getValue());
        if (block != null) return block;

        if (difficulty == DomainDifficulty.MODERATE) {
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

        binding.txtTurnHudNameX.setText(playerName);
        binding.txtTurnHudNameO.setText(rivalName);

        if (!matchStarted || gameManager.isGameOver()) {
            lastTurnProgressIsX = null;

            binding.progressTurnHudX.setProgress(0);
            binding.progressTurnHudO.setProgress(0);

            styleTurnName(binding.txtTurnHudNameX, false);
            styleTurnName(binding.txtTurnHudNameO, false);
            return;
        }

        boolean isXTurn = isOnlineMatch ? "X".equals(turnOnline) : state.isXTurn();
        styleTurnName(binding.txtTurnHudNameX, isXTurn);
        styleTurnName(binding.txtTurnHudNameO, !isXTurn);

        if (isOnlineMatch) {
            if ("X".equals(turnOnline)) binding.progressTurnHudO.setProgress(0);
            else binding.progressTurnHudX.setProgress(0);
            return;
        }

        lastTurnProgressIsX = isXTurn;
    }

    private void styleTurnName(android.widget.TextView textView, boolean active) {
        textView.setTextColor(active ? 0xFFFFFFFF : 0xFFB6C2D1);
        textView.setAlpha(active ? 1f : 0.82f);
        textView.animate()
                .scaleX(active ? 1.03f : 1f)
                .scaleY(active ? 1.03f : 1f)
                .setDuration(140)
                .start();
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

    private void initPlayerServices() {
        profileManager = new ProfileManager();
        profileManager.localProfileRepository = new LocalProfileRepository(this);

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.signInAnonymously().addOnSuccessListener(authResult -> {
            String firebaseUid = Objects.requireNonNull(authResult.getUser()).getUid();
            profileManager.profileRepository = new FirebaseProfileRepository();
            profileManager.profileRepository.loadOrCreateProfile(new ProfileRepository.Callback() {
                @Override public void onSuccess(@NonNull PlayerProfile profile) {
                    profile.uid = firebaseUid;
                    onProfileReady(profile);
                }
                @Override public void onError(@NonNull String error) { loadLocalFallback(); }
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
            @Override public void onSuccess(@NonNull PlayerProfile profile) {
                currentProfile = profile;
                runOnUiThread(() -> {
                    updateHeaderStatus();
                    updateSkillVisuals();
                    Toast.makeText(MainActivity.this, getString(R.string.toast_offline_loaded), Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onError(@NonNull String error) {
                Log.e("Fallback", "Critical error");
                currentProfile = PlayerProfile.createDefault("temp_" + System.currentTimeMillis());
                updateHeaderStatus();
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopOnlineBarAnim();
        try {
            if (onlineSession != null) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void startRematchIntro() {
        matchStarted = false;

        handler.removeCallbacks(onlineTimeoutBannerRunnable);
        stopOnlineBarAnim();
        lastTurnProgressIsX = null;
        binding.progressTurnHudX.setProgress(0);
        binding.progressTurnHudO.setProgress(0);

        setArenaUiVisible(true);

        binding.homeOverlay.setVisibility(View.GONE);
        binding.modeOverlay.setVisibility(View.GONE);

        binding.victoryOverlay.animate().cancel();
        binding.victoryOverlay.setAlpha(0f);
        binding.victoryOverlay.setVisibility(View.GONE);

        resetVersusUiState();
        showVersusOverlay();
    }

    private void resetVersusUiState() {
        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(1f);

        binding.versusBandRoot.setAlpha(1f);
        binding.versusDim.setAlpha(1f);

        binding.txtVersusMode.setAlpha(1f);
        binding.viewVersusStripeTop.setAlpha(1f);
        binding.viewVersusStripeBottom.setAlpha(1f);

        binding.txtBreakX.animate().cancel();
        binding.txtBreakO.animate().cancel();
        binding.txtBreakX.setAlpha(0f);
        binding.txtBreakO.setAlpha(0f);
        binding.txtBreakX.setScaleX(0.6f);
        binding.txtBreakX.setScaleY(0.6f);
        binding.txtBreakO.setScaleX(0.6f);
        binding.txtBreakO.setScaleY(0.6f);
        binding.txtBreakX.setTranslationX(0f);
        binding.txtBreakO.setTranslationX(0f);

        binding.versusBandRoot.animate().cancel();
        binding.versusDim.animate().cancel();
        binding.txtVersusMode.animate().cancel();
        binding.viewVersusStripeTop.animate().cancel();
        binding.viewVersusStripeBottom.animate().cancel();
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
        boolean offlineSelected = (selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT);
        int selectedBg = 0xFF22D3EE;
        int selectedText = 0xFF082F49;
        int defaultBg = 0xFF312E81;
        int defaultText = 0xFFE0E7FF;

        binding.btnModeOffline.setBackgroundTintList(ColorStateList.valueOf(offlineSelected ? selectedBg : defaultBg));
        binding.btnModeOffline.setTextColor(offlineSelected ? selectedText : defaultText);

        binding.btnModeOnline.setBackgroundTintList(ColorStateList.valueOf(offlineSelected ? defaultBg : selectedBg));
        binding.btnModeOnline.setTextColor(offlineSelected ? defaultText : selectedText);
    }

    private void startOfflineVsBot() {
        isOnlineMatch = false;
        onlineSession = null;

        versusBot = true;
        opponentName = randomBotName();
        currentBotDifficulty = randomDifficulty();

        setGameMode();
        gameManager.resetGame();
        binding.victoryLineView.clear();
        setArenaUiVisible(false);

        updateHeaderStatus();
        updateSkillVisuals();
        startMatchIntro();
    }
}
