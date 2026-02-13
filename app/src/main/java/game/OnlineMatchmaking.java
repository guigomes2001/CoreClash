package game;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;

import java.util.HashMap;
import java.util.Map;

import enums.DomainMatchStatus;
import enums.DomainRoomKind;
import util.DateTimeUtil;
import util.FirebaseUtil;
import util.NullUtil;

public class OnlineMatchmaking {

    public interface MatchmakingCallback {
        void onMatched(@NonNull String roomId, boolean isPlayerX, @NonNull String opponentUid);
        void onError(@NonNull String message);
    }

    private static final String TAG = "MATCH_FLOW";

    private static final String STATUS_WAITING = DomainMatchStatus.WAITING.getValue();
    private static final String STATUS_ENDED = DomainMatchStatus.ENDED.getValue();
    private static final String STATUS_MATCHED = DomainMatchStatus.MATCHED.getValue();

    private static final String TURN_X = "X";
    private static final long TURN_DURATION_MS = 10_000L;

    private static final String ROOM_KIND_AUTO = "AUTO_QUEUE";
    private static final String ROOM_KIND_LOCAL_LOBBY = "LOCAL_LOBBY";

    private final DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("rooms");
    private final DatabaseReference autoQueueRef = FirebaseDatabase.getInstance().getReference("matchmaking").child("autoQueue").child("waitingRoomId");

    private static final long ROOM_TTL_MS = 3 * 60 * 1000;
    private static final int MAX_RETRIES = 12;
    private static final long RETRY_DELAY_MS = 300;
    private static final int MAX_ROOM_READY_RETRIES = 80;
    private static final long ROOM_READY_RETRY_DELAY_MS = 250;

    public void findOrCreateMatch(@NonNull String myUid, @NonNull MatchmakingCallback callback) {
        reserveOrCreateQueueRoom(myUid, callback, 0);
    }

    private void reserveOrCreateQueueRoom(@NonNull String myUid, @NonNull MatchmakingCallback callback, int attempt) {
        String candidateRoomId = roomsRef.push().getKey();
        if (NullUtil.isNullOrEmpty(candidateRoomId)) {
            callback.onError("Could not generate a room ID.");
            return;
        }

        final String[] selectedRoomId = new String[]{null};

        autoQueueRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String waitingRoomId = currentData.getValue(String.class);

                if (NullUtil.isNullOrEmpty(waitingRoomId)) {
                    selectedRoomId[0] = candidateRoomId;
                    currentData.setValue(candidateRoomId);
                    return Transaction.success(currentData);
                }

                if (candidateRoomId.equals(waitingRoomId)) {
                    return Transaction.abort();
                }

                selectedRoomId[0] = waitingRoomId;
                currentData.setValue(waitingRoomId);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    retryOrFail(myUid, callback, attempt, "Queue transaction error: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed || NullUtil.isNullOrEmpty(selectedRoomId[0])) {
                    retryOrFail(myUid, callback, attempt, "Queue transaction not committed.");
                    return;
                }

                String selected = selectedRoomId[0];
                if (candidateRoomId.equals(selected)) {
                    Log.d(TAG, "queue host room=" + candidateRoomId + " attempt=" + attempt);
                    createNewRoomTransaction(candidateRoomId, myUid, callback, attempt);
                    return;
                }

                Log.d(TAG, "queue join room=" + selected + " attempt=" + attempt);
                waitForRoomAndJoin(selected, myUid, attempt, 0, callback);
            }
        });
    }


    private void waitForRoomAndJoin(@NonNull String roomId,
                                    @NonNull String myUid,
                                    int queueAttempt,
                                    int readyAttempt,
                                    @NonNull MatchmakingCallback callback) {
        roomsRef.child(roomId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, callback, "room missing");
                return;
            }

            String status = snapshot.child("status").getValue(String.class);
            String xUid = snapshot.child("players").child("X").getValue(String.class);
            String oUid = snapshot.child("players").child("O").getValue(String.class);
            String roomKind = snapshot.child("roomKind").getValue(String.class);

            if (myUid.equals(xUid) && STATUS_WAITING.equals(status) && NullUtil.isNullOrEmpty(oUid)) {
                Log.d(TAG, "reuse-own-waiting-room room=" + roomId + " queueAttempt=" + queueAttempt);
                callback.onMatched(roomId, true, "");
                return;
            }

            if (!STATUS_WAITING.equals(status)
                    || DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)
                    || NullUtil.isNullOrEmpty(xUid)
                    || !NullUtil.isNullOrEmpty(oUid)
                    || myUid.equals(xUid)) {
                maybeRecoverFromStaleQueue(roomId, myUid, queueAttempt, readyAttempt, callback, status, xUid, oUid, roomKind);
                return;
            }

            attemptJoinRoomTransaction(roomId, myUid, new MatchmakingCallback() {
                @Override
                public void onMatched(@NonNull String matchedRoomId, boolean isPlayerX, @NonNull String opponentUid) {
                    clearQueueIfMatches(matchedRoomId);
                    callback.onMatched(matchedRoomId, isPlayerX, opponentUid);
                }

                @Override
                public void onError(@NonNull String message) {
                    retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, callback, message);
                }
            });
        }).addOnFailureListener(e -> retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, callback, safeMsg(e)));
    }

    private void retryWaitRoom(@NonNull String roomId,
                               @NonNull String myUid,
                               int queueAttempt,
                               int readyAttempt,
                               @NonNull MatchmakingCallback callback,
                               @NonNull String reason) {
        if (readyAttempt >= MAX_ROOM_READY_RETRIES) {
            clearQueueIfMatches(roomId);
            retryOrFail(myUid, callback, queueAttempt, "wait/join timeout room=" + roomId + " reason=" + reason);
            return;
        }

        Log.d(TAG, "wait-room retry=" + readyAttempt + " room=" + roomId + " reason=" + reason);
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> waitForRoomAndJoin(roomId, myUid, queueAttempt, readyAttempt + 1, callback), ROOM_READY_RETRY_DELAY_MS);
    }

    private void maybeRecoverFromStaleQueue(@NonNull String roomId,
                                            @NonNull String myUid,
                                            int queueAttempt,
                                            int readyAttempt,
                                            @NonNull MatchmakingCallback callback,
                                            String status,
                                            String xUid,
                                            String oUid,
                                            String roomKind) {
        boolean terminalRoom = STATUS_MATCHED.equals(status) || STATUS_ENDED.equals(status);
        boolean localLobbyRoom = DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind);
        boolean fullRoom = !NullUtil.isNullOrEmpty(oUid);
        boolean invalidHost = NullUtil.isNullOrEmpty(xUid);
        boolean foreignWaitingConflict = STATUS_WAITING.equals(status) && !NullUtil.isNullOrEmpty(xUid) && myUid.equals(xUid) && !NullUtil.isNullOrEmpty(oUid);

        if (terminalRoom || localLobbyRoom || fullRoom || invalidHost || foreignWaitingConflict) {
            Log.d(TAG, "recover-stale-queue room=" + roomId
                    + " stateStatus=" + status
                    + " x=" + xUid
                    + " o=" + oUid
                    + " kind=" + roomKind
                    + " queueAttempt=" + queueAttempt
                    + " readyAttempt=" + readyAttempt);
            clearQueueIfMatches(roomId);
            retryOrFail(myUid, callback, queueAttempt, "stale queue room=" + roomId + " status=" + status);
            return;
        }

        retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, callback, "room not joinable yet");
    }

    private void clearQueueIfMatches(@NonNull String roomId) {
        autoQueueRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String waitingRoomId = currentData.getValue(String.class);
                if (roomId.equals(waitingRoomId)) {
                    currentData.setValue(null);
                    return Transaction.success(currentData);
                }
                return Transaction.abort();
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    Log.d(TAG, "clearQueueIfMatches error room=" + roomId + " msg=" + safeMsg(error.toException()));
                    return;
                }
                Log.d(TAG, "clearQueueIfMatches room=" + roomId + " committed=" + committed);
            }
        });
    }

    private void createNewRoomTransaction(@NonNull String roomId,
                                          @NonNull String myUid,
                                          @NonNull MatchmakingCallback callback,
                                          int attempt) {
        DatabaseReference roomRef = roomsRef.child(roomId);

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (!NullUtil.isNull(currentData.getValue())) {
                    return Transaction.abort();
                }

                Map<String, Object> room = new HashMap<>();
                room.put("status", STATUS_WAITING);
                room.put("roomKind", DomainRoomKind.AUTO_QUEUE.getValue());
                room.put("createdAt", ServerValue.TIMESTAMP);

                room.put("turn", TURN_X);
                room.put("turnStartedAt", ServerValue.TIMESTAMP);
                room.put("turnDurationMs", TURN_DURATION_MS);

                room.put("turnSeq", 0L);
                room.put("lastTimeoutProcessedSeq", -1L);
                room.put("timeoutStreakX", 0L);
                room.put("timeoutStreakO", 0L);
                room.put("winner", "");
                room.put("endReason", "");

                Map<String, Object> players = new HashMap<>();
                players.put("X", myUid);
                players.put("O", "");
                room.put("players", players);

                Map<String, Object> introReady = new HashMap<>();
                introReady.put("X", false);
                introReady.put("O", false);
                room.put("introReady", introReady);

                currentData.setValue(room);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    retryOrFail(myUid, callback, attempt, "Create room failed: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed) {
                    retryOrFail(myUid, callback, attempt, "Create room transaction not committed.");
                    return;
                }

                callback.onMatched(roomId, true, "");
            }
        });
    }

    private void retryOrFail(@NonNull String myUid, @NonNull MatchmakingCallback callback, int attempt, @NonNull String reason) {
        if (attempt >= MAX_RETRIES) {
            Log.d(TAG, "matchmaking-failed attempt=" + attempt + " reason=" + reason + " uid=" + myUid);
            callback.onError("Unable to find a match. Please try again.");
            return;
        }

        Log.d(TAG, "retry attempt=" + attempt + " reason=" + reason);

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> reserveOrCreateQueueRoom(myUid, callback, attempt + 1), RETRY_DELAY_MS);
    }

    private void attemptJoinRoomTransaction(@NonNull String roomId, @NonNull String myUid, @NonNull MatchmakingCallback callback) {
        DatabaseReference roomRef = roomsRef.child(roomId);

        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) {
                    return Transaction.abort();
                }

                String status = currentData.child("status").getValue(String.class);
                String xUid = currentData.child("players").child("X").getValue(String.class);
                String oUid = currentData.child("players").child("O").getValue(String.class);
                String roomKind = currentData.child("roomKind").getValue(String.class);

                if (!STATUS_WAITING.equals(status)) return Transaction.abort();
                if (DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)) return Transaction.abort();
                if (NullUtil.isNullOrEmpty(xUid)) return Transaction.abort();
                if (!NullUtil.isNullOrEmpty(oUid)) return Transaction.abort();
                if (myUid.equals(xUid)) return Transaction.abort();

                currentData.child("players").child("O").setValue(myUid);
                currentData.child("status").setValue(STATUS_MATCHED);
                currentData.child("startedAt").setValue(ServerValue.TIMESTAMP);

                currentData.child("introReady").child("X").setValue(false);
                currentData.child("introReady").child("O").setValue(false);

                if (NullUtil.isNull(currentData.child("turnSeq").getValue())) currentData.child("turnSeq").setValue(0L);
                if (NullUtil.isNull(currentData.child("lastTimeoutProcessedSeq").getValue())) currentData.child("lastTimeoutProcessedSeq").setValue(-1L);
                if (NullUtil.isNull(currentData.child("timeoutStreakX").getValue())) currentData.child("timeoutStreakX").setValue(0L);
                if (NullUtil.isNull(currentData.child("timeoutStreakO").getValue())) currentData.child("timeoutStreakO").setValue(0L);
                if (NullUtil.isNull(currentData.child("winner").getValue())) currentData.child("winner").setValue("");
                if (NullUtil.isNull(currentData.child("endReason").getValue())) currentData.child("endReason").setValue("");

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    callback.onError("Failed to join room: " + safeMsg(error.toException()));
                    return;
                }

                if (!committed) {
                    callback.onError("Join transaction was not committed.");
                    return;
                }

                String xUid = currentData.child("players").child("X").getValue(String.class);
                callback.onMatched(roomId, false, NullUtil.isNull(xUid) ? "" : xUid);
            }
        });
    }

    public void createLocalLobbyRoom(@NonNull String myUid, @NonNull String roomCode, @NonNull MatchmakingCallback callback) {
        String roomId = roomsRef.push().getKey();
        if (NullUtil.isNullOrEmptyOrZero(roomId)) {
            callback.onError("Could not generate a room ID.");
            return;
        }

        DatabaseReference roomRef = roomsRef.child(roomId);
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (!NullUtil.isNull(currentData.getValue())) {
                    return Transaction.abort();
                }

                Map<String, Object> room = new HashMap<>();
                room.put("status", STATUS_WAITING);
                room.put("createdAt", ServerValue.TIMESTAMP);
                room.put("turn", TURN_X);
                room.put("turnStartedAt", ServerValue.TIMESTAMP);
                room.put("turnDurationMs", TURN_DURATION_MS);
                room.put("turnSeq", 0L);
                room.put("lastTimeoutProcessedSeq", -1L);
                room.put("timeoutStreakX", 0L);
                room.put("timeoutStreakO", 0L);
                room.put("winner", "");
                room.put("endReason", "");
                room.put("roomCode", roomCode);
                room.put("roomKind", DomainRoomKind.LOCAL_LOBBY.getValue());

                Map<String, Object> players = new HashMap<>();
                players.put("X", myUid);
                players.put("O", "");
                room.put("players", players);

                Map<String, Object> introReady = new HashMap<>();
                introReady.put("X", false);
                introReady.put("O", false);
                room.put("introReady", introReady);

                currentData.setValue(room);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    callback.onError("Failed to create lobby room: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed) {
                    callback.onError("Could not create local lobby due to concurrency conflict.");
                    return;
                }
                callback.onMatched(roomId, true, "");
            }
        });
    }

    public void joinLocalLobbyRoom(@NonNull String myUid, @NonNull String roomCode, @NonNull MatchmakingCallback callback) {
        roomsRef.orderByChild("roomCode")
                .equalTo(roomCode)
                .limitToFirst(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        callback.onError("Room code not found.");
                        return;
                    }

                    for (DataSnapshot roomSnap : snapshot.getChildren()) {
                        String roomId = roomSnap.getKey();
                        if (NullUtil.isNull(roomId)) {
                            continue;
                        }
                        attemptJoinRoomTransaction(roomId, myUid, callback);
                        return;
                    }
                    callback.onError("Room code not found.");
                })
                .addOnFailureListener(e -> callback.onError("Failed to join room: " + safeMsg(e)));
    }

    public void cleanupOldWaitingRooms() {
        roomsRef.orderByChild("status")
                .equalTo(STATUS_WAITING)
                .limitToFirst(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        return;
                    }

                    for (DataSnapshot s : snapshot.getChildren()) {
                        String roomId = s.getKey();
                        Long createdAt = s.child("createdAt").getValue(Long.class);
                        if (NullUtil.isNull(roomId) || NullUtil.isNull(createdAt)) {
                            continue;
                        }

                        long now = DateTimeUtil.nowMillis();
                        if (now - createdAt > ROOM_TTL_MS) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("status", STATUS_ENDED);
                            updates.put("endedAt", ServerValue.TIMESTAMP);
                            roomsRef.child(roomId).updateChildren(updates);
                        }
                    }
                });
    }

    private String safeMsg(Throwable e) {
        return FirebaseUtil.safeErrorMessage(e, "Unknown error.");
    }
}
