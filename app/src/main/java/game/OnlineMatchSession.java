package game;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.function.Consumer;

import enums.DomainActionType;
import enums.DomainMatchStatus;
import enums.DomainSymmetries;
import util.NullUtil;

public class OnlineMatchSession {

    public interface ActionListener {
        void onRemoteMove(int row, int col, @NonNull String byUid);
        void onRemoteTriangle(@NonNull String byUid);
        void onRemoteSquare(@NonNull String byUid);
        void onOpponentLeft();
    }

    public interface TurnClockListener {
        void onTurnClock(@NonNull String turn, long turnStartedAtMs, long turnDurationMs, long turnSeq, long serverNowApproxMs);
    }

    public interface TurnAdvanceCallback {
        void onResult(boolean advanced);
    }

    public interface IntroReadyListener {
        void onBothReady();
    }

    public interface IntroClockListener {
        void onIntroClock(long startAtMs, long durationMs, long serverNowApproxMs);
    }

    private static final String TAG = "RTDB";

    private static final String STATUS_PLAYING = DomainMatchStatus.PLAYING.getValue();
    private static final String STATUS_ENDED = DomainMatchStatus.ENDED.getValue();
    private static final String STATUS_ABANDONED = DomainMatchStatus.ABANDONED.getValue();
    private static final String END_ABANDONMENT = DomainMatchStatus.END_ABANDONMENT.getValue();

    private static final String TURN_X = DomainSymmetries.X.getValue();
    private static final String TURN_O = DomainSymmetries.O.getValue();

    private final String roomId;
    private final String myUid;
    private final String mySymbol;

    private final DatabaseReference roomRef;
    private final DatabaseReference actionsRef;
    private final DatabaseReference offsetRef;

    private ChildEventListener actionsListener;
    private ValueEventListener statusListener;
    private ValueEventListener opponentListener;
    private ValueEventListener turnClockListener;
    private ValueEventListener offsetListener;
    private ValueEventListener introReadyListener;
    private ValueEventListener introClockListener;

    private volatile long serverOffsetMs = 0L;
    private boolean opponentLeftNotified = false;

    public OnlineMatchSession(@NonNull String roomId, @NonNull String myUid, @NonNull String mySymbol) {
        this.roomId = roomId;
        this.myUid = myUid;
        this.mySymbol = mySymbol;

        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        this.actionsRef = roomRef.child("actions");
        this.offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");

        startServerOffsetListener();
    }

    public long nowServerApprox() {
        return System.currentTimeMillis() + serverOffsetMs;
    }

    private void startServerOffsetListener() {
        if (!NullUtil.isNull(offsetListener)) return;

        offsetListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long off = snapshot.getValue(Long.class);
                if (!NullUtil.isNull(off)) serverOffsetMs = off;
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        offsetRef.addValueEventListener(offsetListener);
    }

    public void startListening(@NonNull ActionListener listener, @Nullable TurnClockListener clockListener) {
        stopListening();

        opponentLeftNotified = false;

        statusListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (opponentLeftNotified) return;

                String status = snapshot.child("status").getValue(String.class);
                String endReason = snapshot.child("endReason").getValue(String.class);

                boolean abandoned = STATUS_ABANDONED.equals(status)
                        || (STATUS_ENDED.equals(status) && END_ABANDONMENT.equals(endReason));

                if (abandoned) {
                    opponentLeftNotified = true;
                    listener.onOpponentLeft();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.addValueEventListener(statusListener);

        actionsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Action action = snapshot.getValue(Action.class);
                if (NullUtil.isNull(action)) return;
                if (NullUtil.isNull(action.playerUid) || action.playerUid.isEmpty()) return;
                if (NullUtil.isNull(action.actionType) || action.actionType.isEmpty()) return;

                if (action.playerUid.equals(myUid)) return;

                switch (action.actionType) {
                    case DomainActionType.MOVE.getValue():
                        if (!NullUtil.isNull(action.row) && !NullUtil.isNull(action.col) && isValidCell(action.row, action.col)) {
                            listener.onRemoteMove(action.row, action.col, action.playerUid);
                        }
                        break;
                    case DomainActionType.TRIANGLE.getValue():
                        listener.onRemoteTriangle(action.playerUid);
                        break;
                    case DomainActionType.SQUARE.getValue():
                        listener.onRemoteSquare(action.playerUid);
                        break;
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        actionsRef.addChildEventListener(actionsListener);

        if (!NullUtil.isNull(clockListener)) {
            listenTurnClock(clockListener);
        }
    }

    public void stopListening() {
        if (!NullUtil.isNull(actionsListener)) {
            actionsRef.removeEventListener(actionsListener);
            actionsListener = null;
        }
        if (!NullUtil.isNull(statusListener)) {
            roomRef.removeEventListener(statusListener);
            statusListener = null;
        }
        if (!NullUtil.isNull(opponentListener)) {
            roomRef.child("players").child("O").removeEventListener(opponentListener);
            opponentListener = null;
        }
        if (!NullUtil.isNull(turnClockListener)) {
            roomRef.removeEventListener(turnClockListener);
            turnClockListener = null;
        }
        if (!NullUtil.isNull(introReadyListener)) {
            roomRef.child("introReady").removeEventListener(introReadyListener);
            introReadyListener = null;
        }
        if (!NullUtil.isNull(introClockListener)) {
            roomRef.child("intro").removeEventListener(introClockListener);
            introClockListener = null;
        }
    }

    public void listenTurnClock(@NonNull TurnClockListener listener) {
        if (!NullUtil.isNull(turnClockListener)) return;

        turnClockListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (NullUtil.isNull(status) || status.isEmpty() || !STATUS_PLAYING.equals(status)) return;

                String turn = snapshot.child("turn").getValue(String.class);
                Long startedAt = snapshot.child("turnStartedAt").getValue(Long.class);
                Long duration = snapshot.child("turnDurationMs").getValue(Long.class);
                Long turnSeq = snapshot.child("turnSeq").getValue(Long.class);

                if (NullUtil.isNull(turn) || NullUtil.isNull(startedAt) || NullUtil.isNull(duration)) return;
                if (NullUtil.isNull(turnSeq)) turnSeq = 0L;

                listener.onTurnClock(turn, startedAt, duration, turnSeq, nowServerApprox());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.addValueEventListener(turnClockListener);
    }

    public void listenOpponentJoin(@NonNull Consumer<String> onJoined) {
        if (!NullUtil.isNull(opponentListener)) return;

        opponentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String oUid = snapshot.getValue(String.class);
                if (!NullUtil.isNull(oUid) && !oUid.trim().isEmpty()) {
                    onJoined.accept(oUid);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.child("players").child("O").addValueEventListener(opponentListener);
    }

    public void scheduleIntroIfHost(boolean iAmHost, long delayMs, long durationMs) {
        if (!iAmHost) return;

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                if (NullUtil.isNull(status)) return Transaction.abort();

                String oUid = currentData.child("players").child("O").getValue(String.class);
                if (NullUtil.isNull(oUid) || oUid.trim().isEmpty()) return Transaction.abort();

                MutableData intro = currentData.child("intro");
                if (!NullUtil.isNull(intro.child("scheduledAt").getValue())) return Transaction.abort();

                intro.child("scheduledAt").setValue(ServerValue.TIMESTAMP);
                intro.child("delayMs").setValue(delayMs);
                intro.child("durationMs").setValue(durationMs);

                currentData.child("introReady").child(TURN_X).setValue(false);
                currentData.child("introReady").child(TURN_O).setValue(false);

                return Transaction.success(currentData);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) Log.w(TAG, "scheduleIntroIfHost error: " + error.getMessage());
            }
        });
    }

    public void listenIntroClock(@NonNull IntroClockListener listener) {
        if (!NullUtil.isNull(introClockListener)) return;

        introClockListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long scheduledAt = snapshot.child("scheduledAt").getValue(Long.class);
                Long delayMs = snapshot.child("delayMs").getValue(Long.class);
                Long durationMs = snapshot.child("durationMs").getValue(Long.class);

                if (NullUtil.isNull(scheduledAt) || NullUtil.isNull(delayMs) || NullUtil.isNull(durationMs)) return;

                long startAt = scheduledAt + delayMs;
                listener.onIntroClock(startAt, durationMs, nowServerApprox());
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.child("intro").addValueEventListener(introClockListener);
    }

    public void markIntroReady(@NonNull IntroReadyListener listener) {
        roomRef.child("introReady").child(mySymbol).setValue(true);

        if (!NullUtil.isNull(introReadyListener)) return;

        introReadyListener = new ValueEventListener() {
            private boolean fired = false;

            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean xReady = snapshot.child(TURN_X).getValue(Boolean.class);
                Boolean oReady = snapshot.child(TURN_O).getValue(Boolean.class);

                boolean both = Boolean.TRUE.equals(xReady) && Boolean.TRUE.equals(oReady);
                if (both && !fired) {
                    fired = true;
                    listener.onBothReady();
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.child("introReady").addValueEventListener(introReadyListener);
    }

    public void startPlayingWhenIntroFinished() {
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                if (NullUtil.isNull(status)) return Transaction.abort();

                if (STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (STATUS_ENDED.equals(status) || STATUS_ABANDONED.equals(status)) return Transaction.abort();

                Long scheduledAt = currentData.child("intro").child("scheduledAt").getValue(Long.class);
                Long delayMs     = currentData.child("intro").child("delayMs").getValue(Long.class);
                Long durationMs  = currentData.child("intro").child("durationMs").getValue(Long.class);
                if (NullUtil.isNull(scheduledAt) || NullUtil.isNull(delayMs) || NullUtil.isNull(durationMs)) return Transaction.abort();

                long startAt = scheduledAt + delayMs;
                long endAt   = startAt + durationMs;

                Boolean xReady = currentData.child("introReady").child("X").getValue(Boolean.class);
                Boolean oReady = currentData.child("introReady").child("O").getValue(Boolean.class);
                if (!Boolean.TRUE.equals(xReady) || !Boolean.TRUE.equals(oReady)) return Transaction.abort();

                long now = nowServerApprox();
                if (now < endAt) return Transaction.abort();

                currentData.child("status").setValue(STATUS_PLAYING);
                currentData.child("turn").setValue(TURN_X);
                currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);

                if (NullUtil.isNull(currentData.child("turnDurationMs").getValue())) {
                    currentData.child("turnDurationMs").setValue(10_000L);
                }
                if (NullUtil.isNull(currentData.child("turnSeq").getValue())) currentData.child("turnSeq").setValue(0L);
                if (NullUtil.isNull(currentData.child("lastTimeoutProcessedSeq").getValue())) currentData.child("lastTimeoutProcessedSeq").setValue(-1L);
                if (NullUtil.isNull(currentData.child("timeoutStreakX").getValue())) currentData.child("timeoutStreakX").setValue(0L);
                if (NullUtil.isNull(currentData.child("timeoutStreakO").getValue())) currentData.child("timeoutStreakO").setValue(0L);
                if (NullUtil.isNull(currentData.child("winner").getValue())) currentData.child("winner").setValue("");
                if (NullUtil.isNull(currentData.child("endReason").getValue())) currentData.child("endReason").setValue("");

                return Transaction.success(currentData);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) Log.w(TAG, "startPlayingWhenIntroFinished error: " + error.getMessage());
            }
        });
    }

    public static class Action {
        public String playerUid;
        public String actionType;
        public Integer row;
        public Integer col;
        public Action() {}
    }

    public void sendMove(int row, int col) {
        if (!isValidCell(row, col)) return;
        pushActionAuthoritative(DomainActionType.MOVE.getValue(), row, col);
    }

    public void sendTriangle() {
        pushActionAuthoritative(DomainActionType.TRIANGLE.getValue(), null, null);
    }

    public void sendSquare() {
        pushActionAuthoritative(DomainActionType.SQUARE.getValue(), null, null);
    }

    public void advanceTurnIfExpired(@NonNull String expectedTurn, long expectedTurnSeq, @NonNull TurnAdvanceCallback callback) {
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                String turn = currentData.child("turn").getValue(String.class);
                Long startedAt = currentData.child("turnStartedAt").getValue(Long.class);
                Long duration = currentData.child("turnDurationMs").getValue(Long.class);

                Long turnSeq = currentData.child("turnSeq").getValue(Long.class);
                Long lastProcessed = currentData.child("lastTimeoutProcessedSeq").getValue(Long.class);

                if (!STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (NullUtil.isNull(turn) || !turn.equals(expectedTurn)) return Transaction.abort();
                if (NullUtil.isNull(startedAt) || NullUtil.isNull(duration)) return Transaction.abort();

                if (NullUtil.isNull(turnSeq)) turnSeq = 0L;
                if (NullUtil.isNull(lastProcessed)) lastProcessed = -1L;

                if (turnSeq != expectedTurnSeq) return Transaction.abort();

                if (turnSeq.equals(lastProcessed)) return Transaction.abort();

                long now = nowServerApprox();
                long endAt = startedAt + duration;
                if (now < endAt) return Transaction.abort();

                Long streakX = currentData.child("timeoutStreakX").getValue(Long.class);
                Long streakO = currentData.child("timeoutStreakO").getValue(Long.class);
                if (NullUtil.isNull(streakX)) streakX = 0L;
                if (NullUtil.isNull(streakO)) streakO = 0L;

                boolean timedOutX = TURN_X.equals(turn);
                if (timedOutX) streakX++;
                else streakO++;

                currentData.child("timeoutStreakX").setValue(streakX);
                currentData.child("timeoutStreakO").setValue(streakO);

                currentData.child("lastTimeoutProcessedSeq").setValue(turnSeq);

                long newSeq = turnSeq + 1L;

                long streak = timedOutX ? streakX : streakO;
                if (streak >= 3L) {
                    String winner = timedOutX ? TURN_O : TURN_X;
                    currentData.child("status").setValue(STATUS_ENDED);
                    currentData.child("endedAt").setValue(ServerValue.TIMESTAMP);
                    currentData.child("winner").setValue(winner);
                    currentData.child("endReason").setValue(END_ABANDONMENT);
                    return Transaction.success(currentData);
                }

                String next = TURN_X.equals(turn) ? TURN_O : TURN_X;
                currentData.child("turn").setValue(next);
                currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);
                currentData.child("turnSeq").setValue(newSeq);

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                callback.onResult(NullUtil.isNull(error) && committed);
            }
        });
    }

    private void pushActionAuthoritative(@NonNull String type, @Nullable Integer row, @Nullable Integer col) {
        String actionId = actionsRef.push().getKey();
        if (NullUtil.isNull(actionId)) return;

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                String turn = currentData.child("turn").getValue(String.class);

                if (!STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (NullUtil.isNull(turn) || !turn.equals(mySymbol)) return Transaction.abort();

                MutableData a = currentData.child("actions").child(actionId);
                a.child("actionId").setValue(actionId);
                a.child("playerUid").setValue(myUid);
                a.child("actionType").setValue(type);
                a.child("timestamp").setValue(ServerValue.TIMESTAMP);
                if (!NullUtil.isNull(row)) a.child("row").setValue(row);
                if (!NullUtil.isNull(col)) a.child("col").setValue(col);

                if (TURN_X.equals(mySymbol)) currentData.child("timeoutStreakX").setValue(0L);
                else currentData.child("timeoutStreakO").setValue(0L);

                String next = TURN_X.equals(turn) ? TURN_O : TURN_X;
                currentData.child("turn").setValue(next);
                currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);

                Long seq = currentData.child("turnSeq").getValue(Long.class);
                if (NullUtil.isNull(seq)) seq = 0L;
                currentData.child("turnSeq").setValue(seq + 1L);

                if (NullUtil.isNull(currentData.child("turnDurationMs").getValue())) {
                    currentData.child("turnDurationMs").setValue(10_000L);
                }
                if (NullUtil.isNull(currentData.child("lastTimeoutProcessedSeq").getValue())) {
                    currentData.child("lastTimeoutProcessedSeq").setValue(-1L);
                }
                if (NullUtil.isNull(currentData.child("timeoutStreakX").getValue())) currentData.child("timeoutStreakX").setValue(0L);
                if (NullUtil.isNull(currentData.child("timeoutStreakO").getValue())) currentData.child("timeoutStreakO").setValue(0L);

                return Transaction.success(currentData);
            }

            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    Log.w(TAG, "Action rejected: " + error.getMessage());
                } else if (!committed) {
                    Log.d(TAG, "Action not committed (not your turn or room not playing).");
                }
            }
        });
    }

    public void endRoom() {
        roomRef.child("status").setValue(STATUS_ENDED);
        roomRef.child("endReason").setValue(END_ABANDONMENT);
        roomRef.child("endedAt").setValue(ServerValue.TIMESTAMP);
    }

    public String getRoomId() {
        return roomId;
    }

    private boolean isValidCell(int r, int c) {
        return r >= 0 && r <= 2 && c >= 0 && c <= 2;
    }
}
