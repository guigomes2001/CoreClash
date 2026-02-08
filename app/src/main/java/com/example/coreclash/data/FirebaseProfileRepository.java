package com.example.coreclash.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseProfileRepository implements ProfileRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    private static final String COLLECTION_NAME = "profiles";
    public FirebaseProfileRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    @Override
    public void loadOrCreateProfile(@NonNull Callback callback) {
        FirebaseUser cachedUser = auth.getCurrentUser();
        if (cachedUser != null) {
            fetchProfile(cachedUser, callback);
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> fetchProfile(result.getUser(), callback))
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Firebase auth failed" : error.getMessage()));
    }

    private void fetchProfile(@NonNull FirebaseUser user, @NonNull Callback callback) {
        firestore.collection(COLLECTION_NAME)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    PlayerProfile profile = snapshot.toObject(PlayerProfile.class);
                    if (profile == null) {
                        profile = PlayerProfile.createDefault(user.getUid());
                        saveProfile(profile);
                    }
                    callback.onSuccess(profile);
                })
                .addOnFailureListener(error -> callback.onError(error.getMessage() == null ? "Firebase read failed" : error.getMessage()));
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        if (profile == null || profile.uid == null) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection(COLLECTION_NAME)
                .document(profile.uid)
                .set(profile)
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Synchronized data!"))
                .addOnFailureListener(e -> Log.e("Firestore", "Error synchronizing", e));
    }
}
