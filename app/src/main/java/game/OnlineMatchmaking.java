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
    private static final long MATCHMAKING_TIMEOUT_MS = 60_000L;
    private static final int MAX_RETRIES = 999;
    private static final long RETRY_DELAY_MS = 300;
    private static final int MAX_ROOM_READY_RETRIES = 80;
    private static final long ROOM_READY_RETRY_DELAY_MS = 250;

    public void findOrCreateMatch(@NonNull String myUid, @NonNull MatchmakingCallback callback) {
        findJoinableRoomOrCreate(myUid, callback, 0, DateTimeUtil.nowMillis());
    }

    private void findJoinableRoomOrCreate(@NonNull String myUid,
                                          @NonNull MatchmakingCallback callback,
                                          int attempt,
                                          long startedAtMs) {
        roomsRef.orderByChild("status")
                .equalTo(STATUS_WAITING)
                .limitToFirst(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        for (DataSnapshot roomSnap : snapshot.getChildren()) {
                            String roomId = roomSnap.getKey();
                            String roomKind = roomSnap.child("roomKind").getValue(String.class);
                            String xUid = roomSnap.child("players").child("X").getValue(String.class);
                            String oUid = roomSnap.child("players").child("O").getValue(String.class);

                            if (NullUtil.isNullOrEmpty(roomId)) continue;
                            if (DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)) continue;
                            if (NullUtil.isNullOrEmpty(xUid)) continue;
                            if (!NullUtil.isNullOrEmpty(oUid)) continue;
                            if (myUid.equals(xUid)) continue;

                            Log.d(TAG, "scan-join-attempt room=" + roomId + " attempt=" + attempt);
                            attemptJoinRoomTransaction(roomId, myUid, new MatchmakingCallback() {
                                @Override
                                public void onMatched(@NonNull String matchedRoomId, boolean isPlayerX, @NonNull String opponentUid) {
                                    callback.onMatched(matchedRoomId, false, opponentUid);
                                }

                                @Override
                                public void onError(@NonNull String message) {
                                    retryOrFail(myUid, callback, attempt, startedAtMs, "scan join failed room=" + roomId + " reason=" + message);
                                }
                            });
                            return;
                        }
                    }

                    String hostRoomId = roomsRef.push().getKey();
                    if (NullUtil.isNullOrEmpty(hostRoomId)) {
                        callback.onError("Could not generate a room ID.");
                        return;
                    }

                    Log.d(TAG, "scan-create-host room=" + hostRoomId + " attempt=" + attempt);
                    createNewRoomTransaction(hostRoomId, myUid, callback, attempt, startedAtMs);
                })
                .addOnFailureListener(e -> retryOrFail(myUid, callback, attempt, startedAtMs, "scan waiting rooms failed: " + safeMsg(e)));
    }

    private void consumeQueueOrCreateHost(@NonNull String myUid,
                                          @NonNull MatchmakingCallback callback,
                                          int attempt,
                                          long startedAtMs) {
        final String[] consumedRoomId = new String[]{null};

        autoQueueRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String waitingRoomId = currentData.getValue(String.class);
                if (NullUtil.isNullOrEmpty(waitingRoomId)) {
                    return Transaction.abort();
                }

                consumedRoomId[0] = waitingRoomId;
                currentData.setValue(null);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    retryOrFail(myUid, callback, attempt, startedAtMs, "Queue consume transaction error: " + safeMsg(error.toException()));
                    return;
                }

                if (committed && !NullUtil.isNullOrEmpty(consumedRoomId[0])) {
                    Log.d(TAG, "queue consumed room=" + consumedRoomId[0] + " attempt=" + attempt);
                    waitForRoomAndJoin(consumedRoomId[0], myUid, attempt, 0, startedAtMs, callback);
                    return;
                }

                createAndPublishHostRoom(myUid, callback, attempt, startedAtMs);
            }
        });
    }

    private void createAndPublishHostRoom(@NonNull String myUid,
                                          @NonNull MatchmakingCallback callback,
                                          int attempt,
                                          long startedAtMs) {
        String hostRoomId = roomsRef.push().getKey();
        if (NullUtil.isNullOrEmpty(hostRoomId)) {
            callback.onError("Could not generate a room ID.");
            return;
        }

        createNewRoomTransaction(hostRoomId, myUid, new MatchmakingCallback() {
            @Override
            public void onMatched(@NonNull String roomId, boolean isPlayerX, @NonNull String opponentUid) {
                publishCreatedRoomOrJoinExisting(roomId, myUid, attempt, startedAtMs, callback);
            }

            @Override
            public void onError(@NonNull String message) {
                retryOrFail(myUid, callback, attempt, startedAtMs, "Create host room failed before queue publish: " + message);
            }
        }, attempt, startedAtMs);
    }

    private void publishCreatedRoomOrJoinExisting(@NonNull String hostRoomId,
                                                  @NonNull String myUid,
                                                  int attempt,
                                                  long startedAtMs,
                                                  @NonNull MatchmakingCallback callback) {
        final String[] selectedRoomId = new String[]{null};

        autoQueueRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String waitingRoomId = currentData.getValue(String.class);

                if (NullUtil.isNullOrEmpty(waitingRoomId)) {
                    selectedRoomId[0] = hostRoomId;
                    currentData.setValue(hostRoomId);
                    return Transaction.success(currentData);
                }

                if (hostRoomId.equals(waitingRoomId)) {
                    selectedRoomId[0] = hostRoomId;
                    return Transaction.success(currentData);
                }

                selectedRoomId[0] = waitingRoomId;
                currentData.setValue(null);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    cleanupCreatedHostRoom(hostRoomId);
                    retryOrFail(myUid, callback, attempt, startedAtMs, "Queue publish transaction error: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed || NullUtil.isNullOrEmpty(selectedRoomId[0])) {
                    cleanupCreatedHostRoom(hostRoomId);
                    retryOrFail(myUid, callback, attempt, startedAtMs, "Queue publish transaction not committed.");
                    return;
                }

                String selected = selectedRoomId[0];
                if (hostRoomId.equals(selected)) {
                    Log.d(TAG, "queue host published room=" + hostRoomId + " attempt=" + attempt);
                    callback.onMatched(hostRoomId, true, "");
                    return;
                }

                Log.d(TAG, "queue host consumed-existing room=" + selected + " ownRoom=" + hostRoomId + " attempt=" + attempt);
                cleanupCreatedHostRoom(hostRoomId);
                waitForRoomAndJoin(selected, myUid, attempt, 0, startedAtMs, callback);
            }
        });
    }

    private void cleanupCreatedHostRoom(@NonNull String roomId) {
        DatabaseReference roomRef = roomsRef.child(roomId);
        roomRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (NullUtil.isNull(currentData.getValue())) {
                    return Transaction.abort();
                }

                String status = currentData.child("status").getValue(String.class);
                String oUid = currentData.child("players").child("O").getValue(String.class);
                if (!STATUS_WAITING.equals(status) || !NullUtil.isNullOrEmpty(oUid)) {
                    return Transaction.abort();
                }

                currentData.child("status").setValue(STATUS_ENDED);
                currentData.child("endedAt").setValue(ServerValue.TIMESTAMP);
                currentData.child("endReason").setValue("queue_recycled");
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (!NullUtil.isNull(error)) {
                    Log.d(TAG, "cleanupCreatedHostRoom error room=" + roomId + " msg=" + safeMsg(error.toException()));
                    return;
                }
                Log.d(TAG, "cleanupCreatedHostRoom room=" + roomId + " committed=" + committed);
            }
        });
    }

    private void waitForRoomAndJoin(@NonNull String roomId,
                                    @NonNull String myUid,
                                    int queueAttempt,
                                    int readyAttempt,
                                    long startedAtMs,
                                    @NonNull MatchmakingCallback callback) {
        roomsRef.child(roomId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                if (readyAttempt >= 4) {
                    clearQueueIfMatches(roomId);
                    retryOrFail(myUid, callback, queueAttempt, startedAtMs, "room missing after retries");
                    return;
                }
                retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, startedAtMs, callback, "room missing");
                return;
            }

            String status = snapshot.child("status").getValue(String.class);
            String xUid = snapshot.child("players").child("X").getValue(String.class);
            String oUid = snapshot.child("players").child("O").getValue(String.class);
            String roomKind = snapshot.child("roomKind").getValue(String.class);

            Log.d(TAG, "waitForRoomAndJoin room=" + roomId
                    + " exists=" + snapshot.exists()
                    + " status=" + status
                    + " roomKind=" + roomKind
                    + " xUid=" + xUid
                    + " oUid=" + oUid
                    + " queueAttempt=" + queueAttempt
                    + " readyAttempt=" + readyAttempt);

            if (myUid.equals(xUid) && STATUS_WAITING.equals(status) && NullUtil.isNullOrEmpty(oUid)) {
                Log.d(TAG, "reuse-own-waiting-room room=" + roomId + " queueAttempt=" + queueAttempt);
                callback.onMatched(roomId, true, "");
                return;
            }

            if (myUid.equals(oUid) && !NullUtil.isNullOrEmpty(xUid)
                    && (STATUS_WAITING.equals(status) || STATUS_MATCHED.equals(status))) {
                Log.d(TAG, "recover-joined-as-o room=" + roomId + " queueAttempt=" + queueAttempt + " readyAttempt=" + readyAttempt);
                clearQueueIfMatches(roomId);
                callback.onMatched(roomId, false, xUid);
                return;
            }

            if (myUid.equals(xUid) && !NullUtil.isNullOrEmpty(oUid)
                    && (STATUS_WAITING.equals(status) || STATUS_MATCHED.equals(status))) {
                Log.d(TAG, "recover-host-with-opponent room=" + roomId + " queueAttempt=" + queueAttempt + " readyAttempt=" + readyAttempt);
                callback.onMatched(roomId, true, oUid);
                return;
            }

            if (!STATUS_WAITING.equals(status)
                    || DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)
                    || NullUtil.isNullOrEmpty(xUid)
                    || !NullUtil.isNullOrEmpty(oUid)
                    || myUid.equals(xUid)) {
                maybeRecoverFromStaleQueue(roomId, myUid, queueAttempt, readyAttempt, startedAtMs, callback, status, xUid, oUid, roomKind);
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
                    retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, startedAtMs, callback, message);
                }
            });
        }).addOnFailureListener(e -> retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, startedAtMs, callback, safeMsg(e)));
    }

    private void retryWaitRoom(@NonNull String roomId,
                               @NonNull String myUid,
                               int queueAttempt,
                               int readyAttempt,
                               long startedAtMs,
                               @NonNull MatchmakingCallback callback,
                               @NonNull String reason) {
        if (readyAttempt >= MAX_ROOM_READY_RETRIES) {
            clearQueueIfMatches(roomId);
            retryOrFail(myUid, callback, queueAttempt, startedAtMs, "wait/join timeout room=" + roomId + " reason=" + reason);
            return;
        }

        Log.d(TAG, "wait-room retry=" + readyAttempt + " room=" + roomId + " reason=" + reason);
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> waitForRoomAndJoin(roomId, myUid, queueAttempt, readyAttempt + 1, startedAtMs, callback), ROOM_READY_RETRY_DELAY_MS);
    }

    private void maybeRecoverFromStaleQueue(@NonNull String roomId,
                                            @NonNull String myUid,
                                            int queueAttempt,
                                            int readyAttempt,
                                            long startedAtMs,
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
            retryOrFail(myUid, callback, queueAttempt, startedAtMs, "stale queue room=" + roomId + " status=" + status);
            return;
        }

        retryWaitRoom(roomId, myUid, queueAttempt, readyAttempt, startedAtMs, callback, "room not joinable yet");
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
                                          int attempt,
                                          long startedAtMs) {
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
                    retryOrFail(myUid, callback, attempt, startedAtMs, "Create room failed: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed) {
                    retryOrFail(myUid, callback, attempt, startedAtMs, "Create room transaction not committed.");
                    return;
                }

                callback.onMatched(roomId, true, "");
            }
        });
    }

    private void retryOrFail(@NonNull String myUid, @NonNull MatchmakingCallback callback, int attempt, long startedAtMs, @NonNull String reason) {
        long elapsedMs = DateTimeUtil.nowMillis() - startedAtMs;
        if (elapsedMs >= MATCHMAKING_TIMEOUT_MS || attempt >= MAX_RETRIES) {
            Log.d(TAG, "matchmaking-failed attempt=" + attempt + " elapsedMs=" + elapsedMs + " timeoutMs=" + MATCHMAKING_TIMEOUT_MS + " reason=" + reason + " uid=" + myUid);
            callback.onError("Unable to find a match. Please try again.");
            return;
        }

        Log.d(TAG, "retry attempt=" + attempt + " elapsedMs=" + elapsedMs + " reason=" + reason);

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> findJoinableRoomOrCreate(myUid, callback, attempt + 1, startedAtMs), RETRY_DELAY_MS);
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
