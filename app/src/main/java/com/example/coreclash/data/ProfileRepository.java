package com.example.coreclash.data;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;

public interface ProfileRepository {

    interface Callback {
        void onSuccess(@NonNull PlayerProfile profile);

        void onError(@NonNull String error);
    }

    void loadOrCreateProfile(@NonNull Callback callback);

    void saveProfile(@NonNull PlayerProfile profile);
}
