package manager;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import enums.DomainSymmetries;
import game.GameState;
import game.OnlineMatchSession;
import game.OnlineMatchmaking;

import java.util.function.Supplier;

public class MatchManager {

    public interface Callbacks {
        void runOnUi(@NonNull Runnable r);

        void onUpdateHeaderStatus();
        void onUpdateSkillVisuals();

        void onShowWaitingOpponentUi();
        void onRestoreMenuButtons();

        void onSetArenaUiVisible(boolean visible);

        void onBeforeOnlineMatchStart();

        void onPlayTimeoutBanner(boolean xSide);
        void onHideTimeoutBanner(boolean xSide);

        boolean isBothIntroReady();

        void onOnlineMatchShouldStartPlaying();

        void onEndOnlineSessionToMenu();
    }

    private final Context context;
    private final android.os.Handler handler;
    private final ActivityMainBinding binding;
    private final GameManager gameManager;
    private final GameState state;
    private final Callbacks cb;

    private final OnlineMatchmaking matchmaking = new OnlineMatchmaking();

    private OnlineMatchSession onlineSession;

    private boolean isOnlineMatch = false;
    private boolean iAmXOnline = true;

    private String mySymbolOnline = "X";
    private String turnOnline = "X";

    private long turnStartedAtOnlineMs = 0L;
    private long turnDurationOnlineMs = 10_000L;

    private long turnSeqOnline = 0L;

    private ValueAnimator onlineBarAnim;

    private String scheduledTimeoutTurnKey = "";
    private String lastTimeoutBannerTurnKey = "";

    private final Runnable onlineTimeoutBannerRunnable = this::maybeShowOnlineTimeoutBanner;

    private String opponentName = "";

    public MatchManager(
            @NonNull Context context,
            @NonNull android.os.Handler handler,
            @NonNull ActivityMainBinding binding,
            @NonNull GameManager gameManager,
            @NonNull GameState state,
            @NonNull Callbacks callbacks
    ) {
        this.context = context;
        this.handler = handler;
        this.binding = binding;
        this.gameManager = gameManager;
        this.state = state;
        this.cb = callbacks;
    }

    public boolean isOnlineMatch() {
        return isOnlineMatch;
    }

    public boolean isMyTurnOnline() {
        return mySymbolOnline.equals(turnOnline);
    }

    public String getMySymbolOnline() {
        return mySymbolOnline;
    }

    public String getTurnOnline() {
        return turnOnline;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public OnlineMatchSession getOnlineSession() {
        return onlineSession;
    }

    public void startOnlineMatchmaking(@NonNull Supplier<String> myUidSupplier) {
        String myUid = myUidSupplier.get();
        if (myUid == null) {
            Toast.makeText(context, "Authentication is not ready yet. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, context.getString(R.string.toast_looking_match), Toast.LENGTH_SHORT).show();
        matchmaking.cleanupOldWaitingRooms();

        matchmaking.findOrCreateMatch(myUid, new OnlineMatchmaking.MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
                isOnlineMatch = true;
                iAmXOnline = iAmX;

                mySymbolOnline = iAmXOnline ? DomainSymmetries.X.getValue() : DomainSymmetries.O.getValue();

                opponentName = opponentUid.trim().isEmpty()
                        ? context.getString(R.string.status_waiting_opponent)
                        : "Player " + opponentUid.substring(0, Math.min(6, opponentUid.length()));

                onlineSession = new OnlineMatchSession(roomId, myUid, mySymbolOnline);

                hookOnlineListenersInternal();

                turnOnline = DomainSymmetries.X.getValue();
                turnStartedAtOnlineMs = 0L;
                turnDurationOnlineMs = 10_000L;
                turnSeqOnline = 0L;

                lastTimeoutBannerTurnKey = "";
                scheduledTimeoutTurnKey = "";

                cb.onBeforeOnlineMatchStart();
                cb.onSetArenaUiVisible(false);

                cb.onUpdateHeaderStatus();
                cb.onUpdateSkillVisuals();

                if (iAmXOnline && opponentUid.trim().isEmpty()) {
                    cb.onShowWaitingOpponentUi();
                }

                onlineSession.listenOpponentJoin(oUid -> cb.runOnUi(() -> {
                    opponentName = "Player " + oUid.substring(0, Math.min(6, oUid.length()));
                    cb.onUpdateHeaderStatus();

                    if (iAmXOnline) {
                        onlineSession.scheduleIntroIfHost(true, 500L, 3000L);
                    }
                }));

                onlineSession.listenIntroClock((startAt, durationMs, serverNow) -> {
                    long delay = Math.max(0L, startAt - serverNow);
                    cb.runOnUi(() -> handler.postDelayed(() -> {
                        if (!isOnlineMatch || onlineSession == null) return;
                        cb.onRestoreMenuButtons();
                        cb.onOnlineMatchShouldStartPlaying();
                    }, delay));
                });
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hookOnlineListenersInternal() {
        if (onlineSession == null) return;

        onlineSession.startListening(
                new OnlineMatchSession.ActionListener() {
                    @Override
                    public void onRemoteMove(int r, int c, @NonNull String byUid) {
                        cb.runOnUi(() -> playTurnRemote(r, c));
                    }

                    @Override
                    public void onRemoteTriangle(@NonNull String byUid) {
                        cb.runOnUi(() -> {
                            gameManager.useTriangle();
                            cb.onUpdateHeaderStatus();
                            cb.onUpdateSkillVisuals();
                        });
                    }

                    @Override
                    public void onRemoteSquare(@NonNull String byUid) {
                        cb.runOnUi(() -> {
                            gameManager.useSquare();
                            cb.onUpdateHeaderStatus();
                            cb.onUpdateSkillVisuals();
                        });
                    }

                    @Override
                    public void onOpponentLeft() {
                        cb.runOnUi(() -> {
                            Toast.makeText(context, context.getString(R.string.toast_opponent_left), Toast.LENGTH_SHORT).show();
                            endOnlineSessionToMenu();
                        });
                    }
                },
                (turn, turnStartedAtMs, turnDurationMs, turnSeq, serverNowApproxMs) -> {
                    turnOnline = turn;
                    turnStartedAtOnlineMs = turnStartedAtMs;
                    turnDurationOnlineMs = turnDurationMs;
                    turnSeqOnline = turnSeq;

                    cb.runOnUi(() -> {
                        cb.onUpdateHeaderStatus();
                        if (cb.isBothIntroReady()) {
                            startOrUpdateOnlineBar();
                            scheduleOnlineTimeoutBanner();
                        } else {
                            stopOnlineBarAnim(true);
                        }
                    });
                }
        );
    }

    public void sendMove(int r, int c) {
        if (onlineSession != null) onlineSession.sendMove(r, c);
    }

    public void sendTriangle() {
        if (onlineSession != null) onlineSession.sendTriangle();
    }

    public void sendSquare() {
        if (onlineSession != null) onlineSession.sendSquare();
    }

    public boolean playTurnLocal(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        boolean won = gameManager.play(r, c);
        return beforeMoves != gameManager.getFinalMoves() && won;
    }

    public boolean playTurnRemote(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        boolean won = gameManager.play(r, c);
        return beforeMoves != gameManager.getFinalMoves() && won;
    }

    public void startOrUpdateOnlineBar() {
        stopOnlineBarAnim(false);

        long nowServer = (onlineSession != null) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long elapsed = Math.max(0L, nowServer - turnStartedAtOnlineMs);
        long remaining = Math.max(0L, turnDurationOnlineMs - elapsed);

        int startProgress = (int) (100f * (remaining / (float) turnDurationOnlineMs));

        final boolean xTurn = "X".equals(turnOnline);
        final ProgressBar active = xTurn ? binding.progressTurnHudX : binding.progressTurnHudO;
        final ProgressBar inactive = xTurn ? binding.progressTurnHudO : binding.progressTurnHudX;

        inactive.setProgress(0);
        active.setProgress(startProgress);

        onlineBarAnim = ValueAnimator.ofInt(startProgress, 0);
        onlineBarAnim.setDuration(remaining);
        onlineBarAnim.setInterpolator(new LinearInterpolator());
        onlineBarAnim.addUpdateListener(a -> active.setProgress((int) a.getAnimatedValue()));
        onlineBarAnim.start();
    }

    public void stopOnlineBarAnim(boolean clearTimeoutBanner) {
        if (onlineBarAnim != null) {
            onlineBarAnim.cancel();
            onlineBarAnim = null;
        }
        handler.removeCallbacks(onlineTimeoutBannerRunnable);
        scheduledTimeoutTurnKey = "";

        if (clearTimeoutBanner) {
            cb.onHideTimeoutBanner(true);
            cb.onHideTimeoutBanner(false);
        }
    }

    public void scheduleOnlineTimeoutBanner() {
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
        cb.onPlayTimeoutBanner(timedOutX);

        if (onlineSession != null) {
            onlineSession.advanceTurnIfExpired(turnOnline, turnSeqOnline, advanced -> {
                if (!advanced) Log.d("RTDB", "Timeout turn advance skipped or already advanced.");
            });
        }
    }

    public void endOnlineSessionToMenu() {
        stopOnlineBarAnim(true);
        try {
            if (onlineSession != null) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}

        onlineSession = null;
        isOnlineMatch = false;

        lastTimeoutBannerTurnKey = "";
        scheduledTimeoutTurnKey = "";
        turnSeqOnline = 0L;

        cb.onEndOnlineSessionToMenu();
    }

    public void onDestroy() {
        stopOnlineBarAnim(true);
        try {
            if (onlineSession != null) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}
    }
}
