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

public class OnlineMatchSession {

    public interface ActionListener {
        void onRemoteMove(int row, int col, @NonNull String byUid);
        void onRemoteTriangle(@NonNull String byUid);
        void onRemoteSquare(@NonNull String byUid);
        void onOpponentLeft();
    }

    public interface TurnClockListener {
        /**
         * @param turn "X" or "O"
         * @param turnStartedAtMs Server timestamp (ms)
         * @param turnDurationMs Total turn duration
         * @param serverNowApproxMs Approximate server time (now)
         */
        void onTurnClock(@NonNull String turn, long turnStartedAtMs, long turnDurationMs, long serverNowApproxMs);
    }

    public interface TurnAdvanceCallback {
        void onResult(boolean advanced);
    }

    public interface IntroReadyListener {
        void onBothReady();
    }

    public static class Action {
        public String actionId;
        public String playerUid;
        public String actionType;
        public Long timestamp;
        public Integer row;
        public Integer col;

        public Action() {}
    }

    private static final String TAG = "RTDB";

    private static final String STATUS_ENDED = "ENDED";
    private static final String STATUS_ABANDONED = "ABANDONED";
    private static final String STATUS_PLAYING = "PLAYING";

    private static final String TURN_X = "X";
    private static final String TURN_O = "O";

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

    private volatile long serverOffsetMs = 0L;

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

    public void startListening(@NonNull ActionListener listener) {
        startListening(listener, null);
    }

    public void startListening(@NonNull ActionListener listener, @Nullable TurnClockListener clockListener) {
        stopListening();

        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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

                Long ts = action.timestamp;
                if (ts != null) {
                    long latency = nowServerApprox() - ts;
                    Log.d(TAG, "Latency ms = " + latency);
                }

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
    }

    public void listenTurnClock(@NonNull TurnClockListener listener) {
        if (turnClockListener != null) return;

        turnClockListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (status == null || !STATUS_PLAYING.equals(status)) return;

                String turn = snapshot.child("turn").getValue(String.class);
                Long startedAt = snapshot.child("turnStartedAt").getValue(Long.class);
                Long duration = snapshot.child("turnDurationMs").getValue(Long.class);

                if (turn == null || startedAt == null || duration == null) return;
                listener.onTurnClock(turn, startedAt, duration, nowServerApprox());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.addValueEventListener(turnClockListener);
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

    public void sendMove(int row, int col) {
        if (!isValidCell(row, col)) return;
        pushActionAuthoritative("MOVE", row, col, true);
    }

    public void sendTriangle() {
        pushActionAuthoritative("TRIANGLE", null, null, true);
    }

    public void sendSquare() {
        pushActionAuthoritative("SQUARE", null, null, true);
    }

    public void advanceTurnIfExpired(@NonNull String expectedTurn, @NonNull TurnAdvanceCallback callback) {
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) return Transaction.abort();

                String status = currentData.child("status").getValue(String.class);
                String turn = currentData.child("turn").getValue(String.class);
                Long startedAt = currentData.child("turnStartedAt").getValue(Long.class);
                Long duration = currentData.child("turnDurationMs").getValue(Long.class);

                if (!STATUS_PLAYING.equals(status)) return Transaction.abort();
                if (turn == null || !turn.equals(expectedTurn)) return Transaction.abort();
                if (startedAt == null || duration == null) return Transaction.abort();

                long now = nowServerApprox();
                long endAt = startedAt + duration;
                if (now < endAt) return Transaction.abort();

                String next = TURN_X.equals(turn) ? TURN_O : TURN_X;
                currentData.child("turn").setValue(next);
                currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                callback.onResult(error == null && committed);
            }
        });
    }

    private void pushActionAuthoritative(@NonNull String type, @Nullable Integer row, @Nullable Integer col, boolean consumesTurn) {
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

                if (consumesTurn) {
                    String next = TURN_X.equals(turn) ? TURN_O : TURN_X;
                    currentData.child("turn").setValue(next);
                    currentData.child("turnStartedAt").setValue(ServerValue.TIMESTAMP);
                    if (currentData.child("turnDurationMs").getValue() == null) {
                        currentData.child("turnDurationMs").setValue(10_000L);
                    }
                }

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

    public void listenOpponentJoin(@NonNull java.util.function.Consumer<String> onJoined) {
        if (opponentListener != null) return;

        opponentListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                String oUid = snapshot.getValue(String.class);
                if (oUid != null && !oUid.isEmpty()) {
                    onJoined.accept(oUid);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        roomRef.child("players").child("O").addValueEventListener(opponentListener);
    }
}
