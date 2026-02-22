package com.example.coreclash.battlepass.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.coreclash.battlepass.model.BpLevel;
import com.example.coreclash.battlepass.model.BpState;
import com.example.coreclash.battlepass.model.Season;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattlePassRepository {

    public interface SeasonStateCallback {
        void onResult(Season season, BpState state);
        void onError(Exception error);
    }

    public interface CompletionCallback {
        void onComplete();
        void onError(Exception error);
    }

    private static final String PREFS = "bp_cache";
    private static final String KEY_QUEUE = "bp_event_queue";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final SharedPreferences prefs;

    public BattlePassRepository(@NonNull Context context) {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void fetchActiveSeasonAndState(@NonNull SeasonStateCallback callback) {
        firestore.collection("battlePassSeasons")
                .whereEqualTo("status", "active")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        callback.onError(new IllegalStateException("No active season"));
                        return;
                    }
                    var document = snapshot.getDocuments().get(0);
                    Season season = Season.fromMap(document.getId(), document.getData());
                    fetchState(season, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    private void fetchState(@NonNull Season season, @NonNull SeasonStateCallback callback) {
        String uid = getUidOrThrow();
        DocumentReference stateRef = firestore.collection("battlePassStates")
                .document(uid + "_" + season.id);
        stateRef.get()
                .addOnSuccessListener(snapshot -> {
                    BpState state;
                    if (!snapshot.exists()) {
                        state = BpState.empty(uid, season.id);
                    } else {
                        state = parseState(uid, season.id, snapshot.getData());
                    }
                    callback.onResult(season, state);
                })
                .addOnFailureListener(callback::onError);
    }

    public void upsertState(@NonNull BpState state, @NonNull CompletionCallback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("seasonId", state.seasonId);
        payload.put("xp", state.xp);
        payload.put("level", state.level);
        payload.put("premiumOwned", state.premiumOwned);
        payload.put("claimedRewardIds", new ArrayList<>(state.claimedRewardIds));
        payload.put("missionProgress", state.missionProgress);
        payload.put("completedMissions", new ArrayList<>(state.completedMissions));
        payload.put("updatedAt", System.currentTimeMillis());

        String docId = state.userId + "_" + state.seasonId;
        firestore.collection("battlePassStates")
                .document(docId)
                .set(payload, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onComplete())
                .addOnFailureListener(error -> {
                    enqueueOfflineEvent(payload);
                    callback.onError(error);
                });
    }

    public void flushOfflineQueue() {
        JSONArray queue = getQueue();
        if (queue.length() == 0) return;

        for (int i = 0; i < queue.length(); i++) {
            JSONObject event = queue.optJSONObject(i);
            if (event == null) continue;
            String seasonId = event.optString("seasonId", "");
            if (seasonId.isBlank()) continue;
            String uid = getUidOrThrow();
            String docId = uid + "_" + seasonId;
            Map<String, Object> payload = jsonToMap(event);
            firestore.collection("battlePassStates")
                    .document(docId)
                    .set(payload, SetOptions.merge());
        }
        prefs.edit().remove(KEY_QUEUE).apply();
    }

    public void fetchLevels(@NonNull String seasonId,
                            @NonNull com.google.android.gms.tasks.OnSuccessListener<List<BpLevel>> listener,
                            @NonNull com.google.android.gms.tasks.OnFailureListener failureListener) {
        firestore.collection("battlePassSeasons")
                .document(seasonId)
                .collection("levels")
                .orderBy("level")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<BpLevel> levels = new ArrayList<>();
                    snapshot.forEach(doc -> levels.add(BpLevel.fromMap(doc.getData())));
                    listener.onSuccess(levels);
                })
                .addOnFailureListener(failureListener);
    }

    private BpState parseState(String uid, String seasonId, Map<String, Object> data) {
        BpState state = BpState.empty(uid, seasonId);
        state.xp = getInt(data, "xp", 0);
        state.level = getInt(data, "level", 1);
        state.premiumOwned = getBoolean(data, "premiumOwned", false);
        Object claimed = data.get("claimedRewardIds");
        if (claimed instanceof List<?>) {
            for (Object rewardId : (List<?>) claimed) {
                if (rewardId instanceof String) state.claimedRewardIds.add((String) rewardId);
            }
        }
        Object progress = data.get("missionProgress");
        if (progress instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) progress).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                    state.missionProgress.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
                }
            }
        }
        Object completed = data.get("completedMissions");
        if (completed instanceof List<?>) {
            for (Object missionId : (List<?>) completed) {
                if (missionId instanceof String) state.completedMissions.add((String) missionId);
            }
        }
        return state;
    }

    private int getInt(Map<String, Object> data, String key, int fallback) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean fallback) {
        Object value = data.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private void enqueueOfflineEvent(Map<String, Object> payload) {
        JSONArray queue = getQueue();
        queue.put(new JSONObject(payload));
        prefs.edit().putString(KEY_QUEUE, queue.toString()).apply();
    }

    private JSONArray getQueue() {
        String raw = prefs.getString(KEY_QUEUE, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private Map<String, Object> jsonToMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        JSONArray names = object.names();
        if (names == null) return map;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i);
            map.put(name, object.opt(name));
        }
        return map;
    }

    private String getUidOrThrow() {
        if (auth.getCurrentUser() == null) throw new IllegalStateException("Authenticated user required");
        return auth.getCurrentUser().getUid();
    }
}
