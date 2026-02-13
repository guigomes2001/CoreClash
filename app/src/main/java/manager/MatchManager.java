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
import com.google.firebase.firestore.FirebaseFirestore;

import enums.DomainSymmetries;
import game.OnlineMatchSession;
import game.OnlineMatchmaking;

import java.util.function.Supplier;
import util.NullUtil;
import util.StringUtil;
import util.ValidationUtil;

public class MatchManager {

    private static final String TAG = "MATCH_FLOW";

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

        void onOnlineRemoteWin(@NonNull String winnerSymbol);
        void onOnlineRemoteDraw();

        void onEndOnlineSessionToMenu();
    }

    private final Context context;
    private final android.os.Handler handler;
    private final ActivityMainBinding binding;
    private final GameManager gameManager;
    private final Callbacks cb;

    private final OnlineMatchmaking matchmaking = new OnlineMatchmaking();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

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
    private final Runnable onlineIntroStartRunnable = this::triggerOnlineIntroStartIfNeeded;

    private String opponentName = "";

    private int matchmakingRequestToken = 0;
    private boolean onlineIntroTriggered = false;

    public MatchManager(
            @NonNull Context context,
            @NonNull android.os.Handler handler,
            @NonNull ActivityMainBinding binding,
            @NonNull GameManager gameManager,
            @NonNull Callbacks callbacks
    ) {
        this.context = context;
        this.handler = handler;
        this.binding = binding;
        this.gameManager = gameManager;
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
        if (!ValidationUtil.isValidUid(myUid)) {
            Toast.makeText(context, context.getString(R.string.auth_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        final int requestToken = ++matchmakingRequestToken;

        matchmaking.cleanupOldWaitingRooms();

        matchmaking.findOrCreateMatch(myUid, new OnlineMatchmaking.MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
                if (requestToken != matchmakingRequestToken) {
                    return;
                }
                bindMatchedRoom(myUid, roomId, iAmX, opponentUid);
            }

            @Override
            public void onError(@NonNull String message) {
                if (requestToken != matchmakingRequestToken) {
                    return;
                }
                cb.onRestoreMenuButtons();
                cb.onSetArenaUiVisible(false);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    public void createLocalLobby(@NonNull String myUid, @NonNull String roomCode) {
        if (!ValidationUtil.isValidUid(myUid) || !ValidationUtil.isValidRoomCode(roomCode)) {
            Toast.makeText(context, context.getString(R.string.error_invalid_room_code), Toast.LENGTH_SHORT).show();
            return;
        }

        matchmaking.createLocalLobbyRoom(myUid, roomCode, new OnlineMatchmaking.MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
                bindMatchedRoom(myUid, roomId, iAmX, opponentUid);
                cb.onShowWaitingOpponentUi();
            }

            @Override
            public void onError(@NonNull String message) {
                cb.onRestoreMenuButtons();
                cb.onSetArenaUiVisible(false);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void joinLocalLobby(@NonNull String myUid, @NonNull String roomCode) {
        if (!ValidationUtil.isValidUid(myUid) || !ValidationUtil.isValidRoomCode(roomCode)) {
            Toast.makeText(context, context.getString(R.string.error_invalid_room_code), Toast.LENGTH_SHORT).show();
            return;
        }

        matchmaking.joinLocalLobbyRoom(myUid, roomCode, new OnlineMatchmaking.MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
                bindMatchedRoom(myUid, roomId, iAmX, opponentUid);
            }

            @Override
            public void onError(@NonNull String message) {
                cb.onRestoreMenuButtons();
                cb.onSetArenaUiVisible(false);
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindMatchedRoom(@NonNull String myUid, @NonNull String roomId, boolean iAmX, @NonNull String opponentUid) {
        isOnlineMatch = true;
        iAmXOnline = iAmX;

        mySymbolOnline = iAmXOnline ? DomainSymmetries.X.getValue() : DomainSymmetries.O.getValue();

        opponentName = StringUtil.isBlank(opponentUid)
                ? context.getString(R.string.status_waiting_opponent)
                : context.getString(R.string.player_short_format, StringUtil.trimOrEmpty(opponentUid).substring(0, Math.min(6, StringUtil.trimOrEmpty(opponentUid).length())));

        if (StringUtil.hasText(opponentUid)) {
            resolveOpponentName(opponentUid);
        }

        onlineSession = new OnlineMatchSession(roomId, myUid, mySymbolOnline);

        hookOnlineListenersInternal();

        turnOnline = DomainSymmetries.X.getValue();
        turnStartedAtOnlineMs = 0L;
        turnDurationOnlineMs = 10_000L;
        turnSeqOnline = 0L;
        onlineIntroTriggered = false;
        handler.removeCallbacks(onlineIntroStartRunnable);

        lastTimeoutBannerTurnKey = "";
        scheduledTimeoutTurnKey = "";

        cb.onBeforeOnlineMatchStart();
        cb.onSetArenaUiVisible(false);

        cb.onUpdateHeaderStatus();
        cb.onUpdateSkillVisuals();

        if (iAmXOnline && StringUtil.isBlank(opponentUid)) {
            cb.onShowWaitingOpponentUi();
        }

        onlineSession.listenOpponentJoin(oUid -> cb.runOnUi(() -> {
            opponentName = context.getString(R.string.player_short_format, oUid.substring(0, Math.min(6, oUid.length())));
            cb.onUpdateHeaderStatus();
            resolveOpponentName(oUid);

            if (iAmXOnline) {
                onlineSession.scheduleIntroIfHost(true, 0L, 0L);
                Log.d(TAG, "host-scheduled-intro room=" + onlineSession.getRoomId());
            }

            handler.postDelayed(onlineIntroStartRunnable, 350L);
        }));


        if (StringUtil.hasText(opponentUid)) {
            handler.postDelayed(onlineIntroStartRunnable, 350L);
        }
        onlineSession.listenIntroClock((startAt, durationMs, serverNow) -> {
            cb.runOnUi(() -> {
                if (!isOnlineMatch || NullUtil.isNull(onlineSession)) {
                    return;
                }
                long waitMs = Math.max(0L, startAt - serverNow);
                Log.d(TAG, "intro-clock room=" + onlineSession.getRoomId()
                        + " startAt=" + startAt
                        + " serverNow=" + serverNow
                        + " waitMs=" + waitMs
                        + " durationMs=" + durationMs);
                handler.removeCallbacks(onlineIntroStartRunnable);
                handler.postDelayed(onlineIntroStartRunnable, waitMs);
            });
        });
    }



    private void triggerOnlineIntroStartIfNeeded() {
        if (!isOnlineMatch || onlineIntroTriggered) {
            return;
        }

        onlineIntroTriggered = true;
        Log.d(TAG, "intro-start-triggered room=" + (NullUtil.isNull(onlineSession) ? "" : onlineSession.getRoomId()));
        cb.onRestoreMenuButtons();
        cb.onOnlineMatchShouldStartPlaying();
    }

    private void resolveOpponentName(@NonNull String opponentUid) {
        db.collection("users").document(opponentUid)
                .get()
                .addOnSuccessListener(doc -> {
                    String displayName = doc.getString("displayName");
                    if (StringUtil.isBlank(displayName)) {
                        return;
                    }
                    cb.runOnUi(() -> {
                        opponentName = StringUtil.trimOrEmpty(displayName);
                        cb.onUpdateHeaderStatus();
                    });
                });
    }

    private void hookOnlineListenersInternal() {
        if (NullUtil.isNull(onlineSession)) {
            return;
        }

        onlineSession.startListening(
                new OnlineMatchSession.ActionListener() {
                    @Override
                    public void onRemoteMove(int r, int c, @NonNull String byUid) {
                        cb.runOnUi(() -> {
                            String symbol = gameManager.getCurrentPlayerSymbol();
                            boolean won = playTurnRemote(r, c);

                            cb.onUpdateHeaderStatus();
                            cb.onUpdateSkillVisuals();

                            if (won) {
                                stopOnlineBarAnim(true);
                                cb.onOnlineRemoteWin(symbol);
                                return;
                            }

                            if (gameManager.isGameOver()) {
                                stopOnlineBarAnim(true);
                                cb.onOnlineRemoteDraw();
                            }
                        });
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
                            if (gameManager.getFinalMoves() > 0) {
                                startOrUpdateOnlineBar();
                                scheduleOnlineTimeoutBanner();
                            } else {
                                stopOnlineBarAnim(true);
                            }
                        } else {
                            stopOnlineBarAnim(true);
                        }
                    });
                }
        );
    }


    public void onBothIntroReady() {
        if (!isOnlineMatch) {
            return;
        }
        if (gameManager.getFinalMoves() <= 0) {
            stopOnlineBarAnim(true);
            return;
        }
        startOrUpdateOnlineBar();
        scheduleOnlineTimeoutBanner();
    }

    public void sendMove(int r, int c) {
        if (!NullUtil.isNull(onlineSession)) onlineSession.sendMove(r, c);
    }

    public void sendTriangle() {
        if (!NullUtil.isNull(onlineSession)) onlineSession.sendTriangle();
    }

    public void sendSquare() {
        if (!NullUtil.isNull(onlineSession)) onlineSession.sendSquare();
    }


    public void forceOnlineStarter(@NonNull String starterSymbol) {
        if (NullUtil.isNull(onlineSession)) {
            return;
        }

        onlineSession.forceTurnTo(starterSymbol, advanced -> {
            if (!advanced) {
                Log.d(TAG, "force starter skipped");
            }
        });
    }

    public boolean playTurnRemote(int r, int c) {
        int beforeMoves = gameManager.getFinalMoves();
        boolean won = gameManager.play(r, c);
        return beforeMoves != gameManager.getFinalMoves() && won;
    }

    public void startOrUpdateOnlineBar() {
        stopOnlineBarAnim(false);

        long nowServer = (!NullUtil.isNull(onlineSession)) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
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
        if (!NullUtil.isNull(onlineBarAnim)) {
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
        if (!isOnlineMatch) {
            return;
        }

        handler.removeCallbacks(onlineTimeoutBannerRunnable);

        long nowServer = (!NullUtil.isNull(onlineSession)) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long endAt = turnStartedAtOnlineMs + turnDurationOnlineMs;
        long delay = Math.max(0L, endAt - nowServer) + 24L;

        scheduledTimeoutTurnKey = turnOnline + ":" + turnStartedAtOnlineMs;
        handler.postDelayed(onlineTimeoutBannerRunnable, delay);
    }

    private void maybeShowOnlineTimeoutBanner() {
        if (!isOnlineMatch) {
            return;
        }

        String currentTurnKey = turnOnline + ":" + turnStartedAtOnlineMs;
        if (!currentTurnKey.equals(scheduledTimeoutTurnKey)) {
            return;
        }

        long nowServer = (!NullUtil.isNull(onlineSession)) ? onlineSession.nowServerApprox() : System.currentTimeMillis();
        long endAt = turnStartedAtOnlineMs + turnDurationOnlineMs;

        if (nowServer < endAt) {
            long extra = Math.max(8L, endAt - nowServer);
            handler.postDelayed(onlineTimeoutBannerRunnable, extra);
            return;
        }

        if (currentTurnKey.equals(lastTimeoutBannerTurnKey)) {
            return;
        }

        lastTimeoutBannerTurnKey = currentTurnKey;

        boolean timedOutX = "X".equals(turnOnline);
        cb.onPlayTimeoutBanner(timedOutX);

        if (!NullUtil.isNull(onlineSession)) {
            onlineSession.advanceTurnIfExpired(turnOnline, turnSeqOnline, advanced -> {
                if (!advanced) Log.d("RTDB", "Timeout turn advance skipped or already advanced.");
            });
        }
    }

    public void endOnlineSessionToMenu() {
        stopOnlineBarAnim(true);
        try {
            if (!NullUtil.isNull(onlineSession)) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}

        onlineSession = null;
        isOnlineMatch = false;

        lastTimeoutBannerTurnKey = "";
        scheduledTimeoutTurnKey = "";
        turnSeqOnline = 0L;
        onlineIntroTriggered = false;
        handler.removeCallbacks(onlineIntroStartRunnable);

        cb.onEndOnlineSessionToMenu();
    }


    public void cancelMatchmakingSearch() {
        matchmakingRequestToken++;

        if (!NullUtil.isNull(onlineSession) && isOnlineMatch) {
            endOnlineSessionToMenu();
            return;
        }

        cb.onRestoreMenuButtons();
        cb.onSetArenaUiVisible(false);
        stopOnlineBarAnim(true);
    }

    public void onDestroy() {
        stopOnlineBarAnim(true);
        try {
            if (!NullUtil.isNull(onlineSession)) {
                onlineSession.stopListening();
                onlineSession.endRoom();
            }
        } catch (Exception ignored) {}
    }
}
