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

import enums.DomainMatchStatus;
import enums.DomainSymmetries;

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

    public OnlineMatchSession(@NonNull String roomId, @NonNull String myUid, @NonNull String mySymbol) {
        this.roomId = roomId;
        this.myUid = myUid;
        this.mySymbol = mySymbol;

        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        this.actionsRef = roomRef.child("actions");
        this.offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");

        startServerOffsetListener();
        enableOnDisconnectAbandon();
    }

    public long nowServerApprox() {
        return System.currentTimeMillis() + serverOffsetMs;
    }

    private void startServerOffsetListener() {
        if (offsetListener != null) return;

        offsetListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long off = snapshot.getValue(Long.class);
                if (off != null) serverOffsetMs = off;
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        offsetRef.addValueEventListener(offsetListener);
    }

    public void enableOnDisconnectAbandon() {
        roomRef.child("status").onDisconnect().setValue(STATUS_ABANDONED);
        roomRef.child("endedAt").onDisconnect().setValue(ServerValue.TIMESTAMP);
    }

    public void startListening(@NonNull ActionListener listener, @Nullable TurnClockListener clockListener) {
        stopListening();

        statusListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (STATUS_ENDED.equals(status) || STATUS_ABANDONED.equals(status)) {
                    listener.onOpponentLeft();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.child("status").addValueEventListener(statusListener);

        actionsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Action action = snapshot.getValue(Action.class);
                if (action == null) return;
                if (action.playerUid == null || action.playerUid.isEmpty()) return;
                if (action.actionType == null || action.actionType.isEmpty()) return;

                if (action.playerUid.equals(myUid)) return;

                switch (action.actionType) {
                    case "MOVE":
                        if (action.row != null && action.col != null && isValidCell(action.row, action.col)) {
                            listener.onRemoteMove(action.row, action.col, action.playerUid);
                        }
                        break;
                    case "TRIANGLE":
                        listener.onRemoteTriangle(action.playerUid);
                        break;
                    case "SQUARE":
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

        if (clockListener != null) {
            listenTurnClock(clockListener);
        }
    }

    public void stopListening() {
        if (actionsListener != null) {
            actionsRef.removeEventListener(actionsListener);
            actionsListener = null;
        }
        if (statusListener != null) {
            roomRef.child("status").removeEventListener(statusListener);
            statusListener = null;
        }
        if (opponentListener != null) {
            roomRef.child("players").child("O").removeEventListener(opponentListener);
            opponentListener = null;
        }
        if (turnClockListener != null) {
            roomRef.removeEventListener(turnClockListener);
            turnClockListener = null;
        }
        if (introReadyListener != null) {
            roomRef.child("introReady").removeEventListener(introReadyListener);
            introReadyListener = null;
        }
        if (introClockListener != null) {
            roomRef.child("intro").removeEventListener(introClockListener);
            introClockListener = null;
        }
    }

    public void listenTurnClock(@NonNull TurnClockListener listener) {
        if (turnClockListener != null) return;

        turnClockListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (status == null || status.isEmpty() || !STATUS_PLAYING.equals(status)) return;

                String turn = snapshot.child("turn").getValue(String.class);
                Long startedAt = snapshot.child("turnStartedAt").getValue(Long.class);
                Long duration = snapshot.child("turnDurationMs").getValue(Long.class);
                Long turnSeq = snapshot.child("turnSeq").getValue(Long.class);

                if (turn == null || startedAt == null || duration == null) return;
                if (turnSeq == null) turnSeq = 0L;

                listener.onTurnClock(turn, startedAt, duration, turnSeq, nowServerApprox());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.addValueEventListener(turnClockListener);
    }

    public void listenOpponentJoin(@NonNull Consumer<String> onJoined) {
        if (opponentListener != null) return;

        opponentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String oUid = snapshot.getValue(String.class);
                if (oUid != null && !oUid.trim().isEmpty()) {
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
                if (currentData.getValue() == null) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                if (status == null) return Transaction.abort();

                String oUid = currentData.child("players").child("O").getValue(String.class);
                if (oUid == null || oUid.trim().isEmpty()) return Transaction.abort();

                MutableData intro = currentData.child("intro");
                if (intro.child("scheduledAt").getValue() != null) return Transaction.abort();

                intro.child("scheduledAt").setValue(ServerValue.TIMESTAMP);
                intro.child("delayMs").setValue(delayMs);
                intro.child("durationMs").setValue(durationMs);

                currentData.child("introReady").child(TURN_X).setValue(false);
                currentData.child("introReady").child(TURN_O).setValue(false);

                return Transaction.success(currentData);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) Log.w(TAG, "scheduleIntroIfHost error: " + error.getMessage());
            }
        });
    }

    public void listenIntroClock(@NonNull IntroClockListener listener) {
        if (introClockListener != null) return;

        introClockListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long scheduledAt = snapshot.child("scheduledAt").getValue(Long.class);
                Long delayMs = snapshot.child("delayMs").getValue(Long.class);
                Long durationMs = snapshot.child("durationMs").getValue(Long.class);

                if (scheduledAt == null || delayMs == null || durationMs == null) return;

                long startAt = scheduledAt + delayMs;
                listener.onIntroClock(startAt, durationMs, nowServerApprox());
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.child("intro").addValueEventListener(introClockListener);
    }

    public void markIntroReady(@NonNull IntroReadyListener listener) {
        roomRef.child("introReady").child(mySymbol).setValue(true);

        if (introReadyListener != null) return;

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
                if (currentData.getValue() == null) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                if (status == null) return Transaction.abort();

                if (STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (STATUS_ENDED.equals(status) || STATUS_ABANDONED.equals(status)) return Transaction.abort();

                Long scheduledAt = currentData.child("intro").child("scheduledAt").getValue(Long.class);
                Long delayMs     = currentData.child("intro").child("delayMs").getValue(Long.class);
                Long durationMs  = currentData.child("intro").child("durationMs").getValue(Long.class);
                if (scheduledAt == null || delayMs == null || durationMs == null) return Transaction.abort();

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

                if (currentData.child("turnDurationMs").getValue() == null) {
                    currentData.child("turnDurationMs").setValue(10_000L);
                }
                if (currentData.child("turnSeq").getValue() == null) currentData.child("turnSeq").setValue(0L);
                if (currentData.child("lastTimeoutProcessedSeq").getValue() == null) currentData.child("lastTimeoutProcessedSeq").setValue(-1L);
                if (currentData.child("timeoutStreakX").getValue() == null) currentData.child("timeoutStreakX").setValue(0L);
                if (currentData.child("timeoutStreakO").getValue() == null) currentData.child("timeoutStreakO").setValue(0L);
                if (currentData.child("winner").getValue() == null) currentData.child("winner").setValue("");
                if (currentData.child("endReason").getValue() == null) currentData.child("endReason").setValue("");

                return Transaction.success(currentData);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) Log.w(TAG, "startPlayingWhenIntroFinished error: " + error.getMessage());
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
        pushActionAuthoritative("MOVE", row, col);
    }

    public void sendTriangle() {
        pushActionAuthoritative("TRIANGLE", null, null);
    }

    public void sendSquare() {
        pushActionAuthoritative("SQUARE", null, null);
    }

    public void advanceTurnIfExpired(@NonNull String expectedTurn, long expectedTurnSeq, @NonNull TurnAdvanceCallback callback) {
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                String turn = currentData.child("turn").getValue(String.class);
                Long startedAt = currentData.child("turnStartedAt").getValue(Long.class);
                Long duration = currentData.child("turnDurationMs").getValue(Long.class);

                Long turnSeq = currentData.child("turnSeq").getValue(Long.class);
                Long lastProcessed = currentData.child("lastTimeoutProcessedSeq").getValue(Long.class);

                if (!STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (turn == null || !turn.equals(expectedTurn)) return Transaction.abort();
                if (startedAt == null || duration == null) return Transaction.abort();

                if (turnSeq == null) turnSeq = 0L;
                if (lastProcessed == null) lastProcessed = -1L;

                if (turnSeq != expectedTurnSeq) return Transaction.abort();

                if (turnSeq.equals(lastProcessed)) return Transaction.abort();

                long now = nowServerApprox();
                long endAt = startedAt + duration;
                if (now < endAt) return Transaction.abort();

                Long streakX = currentData.child("timeoutStreakX").getValue(Long.class);
                Long streakO = currentData.child("timeoutStreakO").getValue(Long.class);
                if (streakX == null) streakX = 0L;
                if (streakO == null) streakO = 0L;

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
                callback.onResult(error == null && committed);
            }
        });
    }

    private void pushActionAuthoritative(@NonNull String type, @Nullable Integer row, @Nullable Integer col) {
        String actionId = actionsRef.push().getKey();
        if (actionId == null) return;

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                String turn = currentData.child("turn").getValue(String.class);

                if (!STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (turn == null || !turn.equals(mySymbol)) return Transaction.abort();

                MutableData a = currentData.child("actions").child(actionId);
                a.child("actionId").setValue(actionId);
                a.child("playerUid").setValue(myUid);
                a.child("actionType").setValue(type);
                a.child("timestamp").setValue(ServerValue.TIMESTAMP);
                if (row != null) a.child("row").setValue(row);
                if (col != null) a.child("col").setValue(col);

                if (TURN_X.equals(mySymbol)) currentData.child("timeoutStreakX").setValue(0L);
                else currentData.child("timeoutStreakO").setValue(0L);

                String next = TURN_X.equals(turn) ? TURN_O : TURN_X;
                currentData.child("turn").setValue(next);
                currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);

                Long seq = currentData.child("turnSeq").getValue(Long.class);
                if (seq == null) seq = 0L;
                currentData.child("turnSeq").setValue(seq + 1L);

                if (currentData.child("turnDurationMs").getValue() == null) {
                    currentData.child("turnDurationMs").setValue(10_000L);
                }
                if (currentData.child("lastTimeoutProcessedSeq").getValue() == null) {
                    currentData.child("lastTimeoutProcessedSeq").setValue(-1L);
                }
                if (currentData.child("timeoutStreakX").getValue() == null) currentData.child("timeoutStreakX").setValue(0L);
                if (currentData.child("timeoutStreakO").getValue() == null) currentData.child("timeoutStreakO").setValue(0L);

                return Transaction.success(currentData);
            }

            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.w(TAG, "Action rejected: " + error.getMessage());
                } else if (!committed) {
                    Log.d(TAG, "Action not committed (not your turn or room not playing).");
                }
            }
        });
    }

    public void endRoom() {
        roomRef.child("status").setValue(STATUS_ENDED);
        roomRef.child("endedAt").setValue(ServerValue.TIMESTAMP);
    }

    public String getRoomId() {
        return roomId;
    }

    private boolean isValidCell(int r, int c) {
        return r >= 0 && r <= 2 && c >= 0 && c <= 2;
    }
}
