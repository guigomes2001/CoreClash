package manager;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import util.NullUtil;

public class SocialManager {

    public interface Callback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    public interface RoomInviteCallback {
        void onSuccess(@NonNull String roomId, @NonNull String inviteId);
        void onError(@NonNull String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void upsertUserProfile(@NonNull String uid, @NonNull String displayName, @NonNull String tag) {
        Map<String, Object> data = new HashMap<>();
        data.put("displayName", displayName);
        data.put("tag", tag);
        data.put("status", "online");

        Map<String, Object> stats = new HashMap<>();
        stats.put("wins", 0);
        stats.put("losses", 0);
        stats.put("draws", 0);
        data.put("stats", stats);

        Map<String, Object> homeAway = new HashMap<>();
        homeAway.put("homeCount", 0);
        homeAway.put("awayCount", 0);
        data.put("homeAway", homeAway);

        db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public void setPresence(@NonNull String uid, @NonNull String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public void sendFriendRequestByTag(@NonNull String myUid, @NonNull String tag, @NonNull Callback callback) {
        db.collection("users")
                .whereEqualTo("tag", tag)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> handleTagLookup(myUid, snapshot, callback))
                .addOnFailureListener(e -> callback.onError(safeError(e)));
    }

    private void handleTagLookup(@NonNull String myUid, @NonNull QuerySnapshot snapshot, @NonNull Callback callback) {
        if (snapshot.isEmpty()) {
            callback.onError("Tag not found.");
            return;
        }

        String friendUid = snapshot.getDocuments().get(0).getId();
        if (friendUid.equals(myUid)) {
            callback.onError("Cannot add yourself.");
            return;
        }

        Map<String, Object> outgoing = new HashMap<>();
        outgoing.put("state", "outgoing");
        outgoing.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> incoming = new HashMap<>();
        incoming.put("state", "incoming");
        incoming.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference myRef = db.collection("users").document(myUid)
                .collection("friends").document(friendUid);
        DocumentReference friendRef = db.collection("users").document(friendUid)
                .collection("friends").document(myUid);

        myRef.set(outgoing)
                .continueWithTask(t -> friendRef.set(incoming))
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(safeError(e)));
    }

    public void createOnlineInvite(@NonNull String fromUid,
                                   @NonNull String toUid,
                                   @NonNull String roomMode,
                                   @NonNull RoomInviteCallback callback) {
        String roomId = UUID.randomUUID().toString();
        String inviteId = UUID.randomUUID().toString();

        Map<String, Object> room = new HashMap<>();
        room.put("mode", roomMode);
        room.put("hostUid", fromUid);
        room.put("guestUid", "");
        room.put("state", "waiting");
        room.put("createdAt", FieldValue.serverTimestamp());
        room.put("expiresAt", Timestamp.now());

        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUid", fromUid);
        invite.put("toUid", toUid);
        invite.put("type", "ONLINE_ROOM");
        invite.put("roomId", roomId);
        invite.put("state", "pending");
        invite.put("createdAt", FieldValue.serverTimestamp());
        invite.put("expiresAt", Timestamp.now());

        db.collection("rooms").document(roomId).set(room)
                .continueWithTask(t -> db.collection("invites").document(inviteId).set(invite))
                .addOnSuccessListener(v -> callback.onSuccess(roomId, inviteId))
                .addOnFailureListener(e -> callback.onError(safeError(e)));
    }

    @NonNull
    private String safeError(Throwable e) {
        if (NullUtil.isNull(e) || NullUtil.isNull(e.getMessage()) || e.getMessage().trim().isEmpty()) {
            return "Unknown error";
        }
        return e.getMessage().trim();
    }
}
