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

    private static final String STATUS_WAITING = DomainMatchStatus.WAITING.getValue();
    private static final String STATUS_ENDED   = DomainMatchStatus.ENDED.getValue();
    private static final String STATUS_MATCHED = DomainMatchStatus.MATCHED.getValue();

    private static final String TURN_X = "X";
    private static final String TURN_O = "O";

    private static final long TURN_DURATION_MS = 10_000L;

    private final DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference("rooms");

    private static final long ROOM_TTL_MS = 3 * 60 * 1000;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 200;

    public void findOrCreateMatch(@NonNull String myUid, @NonNull MatchmakingCallback callback) {
        findOrCreateMatchInternal(myUid, callback, 0);
    }

    private void findOrCreateMatchInternal(@NonNull String myUid, @NonNull MatchmakingCallback callback, int attempt) {
        roomsRef.orderByChild("status")
                .equalTo(STATUS_WAITING)
                 .limitToFirst(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        long now = DateTimeUtil.nowMillis();
                        for (DataSnapshot roomSnap : snapshot.getChildren()) {
                            String roomId = roomSnap.getKey();
                            if (NullUtil.isNull(roomId)) continue;

                            String roomKind = roomSnap.child("roomKind").getValue(String.class);
                            if (DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)) continue;

                            Long createdAt = roomSnap.child("createdAt").getValue(Long.class);
                            if (!NullUtil.isNull(createdAt) && (now - createdAt > ROOM_TTL_MS)) continue;

                            String xUid = roomSnap.child("players").child("X").getValue(String.class);
                            String oUid = roomSnap.child("players").child("O").getValue(String.class);
                            if (NullUtil.isNullOrEmpty(xUid) || !NullUtil.isNullOrEmpty(oUid) || myUid.equals(xUid)) continue;

                            attemptJoinRoomTransaction(roomId, myUid, attempt, callback);
                            return;
                        }
                    }
                    createNewRoomTransaction(myUid, callback);
                })
                .addOnFailureListener(e ->
                        retryOrFail(myUid, callback, attempt, "Matchmaking search failed: " + safeMsg(e))
                );
    }

    private void retryOrFail(@NonNull String myUid, @NonNull MatchmakingCallback callback, int attempt, @NonNull String reason) {
        if (attempt >= MAX_RETRIES) {
            callback.onError("Unable to find a match. Please try again.");
            return;
        }

        Log.d("MM", "Retrying matchmaking (" + attempt + "): " + reason);

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(
                        () -> findOrCreateMatchInternal(myUid, callback, attempt + 1),
                        RETRY_DELAY_MS
                );
    }

    private void createNewRoomTransaction(@NonNull String myUid, @NonNull MatchmakingCallback callback) {
        String roomId = roomsRef.push().getKey();
        if (NullUtil.isNull(roomId)) {
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
            public void onComplete(
                    com.google.firebase.database.DatabaseError error,
                    boolean committed,
                    DataSnapshot currentData
            ) {
                if (!NullUtil.isNull(error)) {
                    callback.onError("Failed to create room: " + safeMsg(error.toException()));
                    return;
                }
                if (!committed) {
                    callback.onError("Failed to create room due to a concurrency conflict. Please try again.");
                    return;
                }
                rebalanceOrConfirmCreatedRoom(roomId, myUid, callback);
            }
        });
    }

    private void rebalanceOrConfirmCreatedRoom(@NonNull String createdRoomId, @NonNull String myUid, @NonNull MatchmakingCallback callback) {
        roomsRef.orderByChild("status")
                .equalTo(STATUS_WAITING)
                .limitToFirst(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String bestRoomToJoin = null;
                    long now = DateTimeUtil.nowMillis();

                    for (DataSnapshot roomSnap : snapshot.getChildren()) {
                        String roomId = roomSnap.getKey();
                        if (NullUtil.isNull(roomId) || createdRoomId.equals(roomId)) continue;

                        String roomKind = roomSnap.child("roomKind").getValue(String.class);
                        if (DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)) continue;

                        Long createdAt = roomSnap.child("createdAt").getValue(Long.class);
                        if (!NullUtil.isNull(createdAt) && (now - createdAt > ROOM_TTL_MS)) continue;

                        String xUid = roomSnap.child("players").child("X").getValue(String.class);
                        String oUid = roomSnap.child("players").child("O").getValue(String.class);

                        if (NullUtil.isNullOrEmpty(xUid) || !NullUtil.isNullOrEmpty(oUid) || myUid.equals(xUid)) continue;

                        if (NullUtil.isNull(bestRoomToJoin) || roomId.compareTo(bestRoomToJoin) < 0) {
                            bestRoomToJoin = roomId;
                        }
                    }

                    if (NullUtil.isNull(bestRoomToJoin)) {
                        callback.onMatched(createdRoomId, true, "");
                        return;
                    }

                    String targetRoomId = bestRoomToJoin;
                    attemptJoinRoomTransaction(targetRoomId, myUid, 0, new MatchmakingCallback() {
                        @Override
                        public void onMatched(@NonNull String roomId, boolean isPlayerX, @NonNull String opponentUid) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("status", STATUS_ENDED);
                            updates.put("endedAt", ServerValue.TIMESTAMP);
                            roomsRef.child(createdRoomId).updateChildren(updates);

                            callback.onMatched(roomId, isPlayerX, opponentUid);
                        }

                        @Override
                        public void onError(@NonNull String message) {
                            callback.onMatched(createdRoomId, true, "");
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onMatched(createdRoomId, true, ""));
    }

    private void attemptJoinRoomTransaction(@NonNull String roomId, @NonNull String myUid, int attempt, @NonNull MatchmakingCallback callback) {
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

                if (!STATUS_WAITING.equals(status)) {
                    return Transaction.abort();
                }
                if (DomainRoomKind.LOCAL_LOBBY.getValue().equals(roomKind)) {
                    return Transaction.abort();
                }
                if (NullUtil.isNullOrEmpty(xUid)) {
                    return Transaction.abort();
                }
                if (!NullUtil.isNullOrEmpty(oUid)) {
                    return Transaction.abort();
                }
                if (myUid.equals(xUid)) {
                    return Transaction.abort();
                }

                currentData.child("players").child("O").setValue(myUid);

                currentData.child("status").setValue(STATUS_MATCHED);
                currentData.child("startedAt").setValue(ServerValue.TIMESTAMP);

                currentData.child("introReady").child(TURN_X).setValue(false);
                currentData.child("introReady").child(TURN_O).setValue(false);

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
                    retryOrFail(myUid, callback, attempt, "Join transaction was not committed.");
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
                        attemptJoinRoomTransaction(roomId, myUid, 0, callback);
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
