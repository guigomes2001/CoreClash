package com.example.coreclash.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;

import java.util.ArrayList;
import java.util.Arrays;

public class LocalProfileRepository implements ProfileRepository {

    private static final String PREF = "coreclash_profile";
    private final SharedPreferences sharedPreferences;

    public LocalProfileRepository(@NonNull Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    @Override
    public void loadOrCreateProfile(@NonNull Callback callback) {
        String uid = sharedPreferences.getString("uid", null);
        if (uid == null) {
            uid = "local_" + System.currentTimeMillis();
            PlayerProfile profile = PlayerProfile.createDefault(uid);
            saveProfile(profile);
            callback.onSuccess(profile);
            return;
        }

        PlayerProfile profile = new PlayerProfile();
        profile.uid = uid;
        profile.displayName = sharedPreferences.getString("displayName", "CorePlayer");
        profile.coins = sharedPreferences.getLong("coins", 120);
        profile.equippedTheme = sharedPreferences.getString("equippedTheme", "ARENA");
        profile.equippedSymbolStyle = sharedPreferences.getString("equippedSymbolStyle", "CLASSIC");
        profile.ownedThemes = new ArrayList<>(Arrays.asList(sharedPreferences.getString("ownedThemes", "ARENA").split(",")));
        profile.ownedSymbolStyles = new ArrayList<>(Arrays.asList(sharedPreferences.getString("ownedStyles", "CLASSIC").split(",")));
        profile.totalWins = sharedPreferences.getInt("totalWins", 0);
        profile.winStreak = sharedPreferences.getInt("winStreak", 0);
        profile.bestWinStreak = sharedPreferences.getInt("bestWinStreak", 0);
        profile.rankedPoints = sharedPreferences.getInt("rankedPoints", 0);
        profile.lastDailyBonusEpochDay = sharedPreferences.getLong("lastDailyBonusEpochDay", 0);
        profile.dailyBonusStreak = sharedPreferences.getInt("dailyBonusStreak", 0);
        callback.onSuccess(profile);
    }

    @Override
    public void saveProfile(@NonNull PlayerProfile profile) {
        sharedPreferences.edit()
                .putString("uid", profile.uid)
                .putString("displayName", profile.displayName)
                .putLong("coins", profile.coins)
                .putString("equippedTheme", profile.equippedTheme)
                .putString("equippedSymbolStyle", profile.equippedSymbolStyle)
                .putString("ownedThemes", String.join(",", profile.ownedThemes))
                .putString("ownedStyles", String.join(",", profile.ownedSymbolStyles))
                .putInt("totalWins", profile.totalWins)
                .putInt("winStreak", profile.winStreak)
                .putInt("bestWinStreak", profile.bestWinStreak)
                .putInt("rankedPoints", profile.rankedPoints)
                .putLong("lastDailyBonusEpochDay", profile.lastDailyBonusEpochDay)
                .putInt("dailyBonusStreak", profile.dailyBonusStreak)
                .apply();
    }
}
