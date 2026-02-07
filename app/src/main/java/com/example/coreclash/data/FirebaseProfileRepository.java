package com.example.coreclash.data;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseProfileRepository implements ProfileRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

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
        firestore.collection("players")
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
    public void saveProfile(@NonNull PlayerProfile profile) {
        firestore.collection("players")
                .document(profile.uid)
                .set(profile);
    }
}
