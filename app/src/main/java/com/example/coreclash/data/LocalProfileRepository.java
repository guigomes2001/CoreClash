package com.example.coreclash.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;

import java.util.ArrayList;
import java.util.Arrays;
import util.DateTimeUtil;
import util.NullUtil;

public class LocalProfileRepository implements ProfileRepository {

    private static final String PREF = "coreclash_profile";
    private final SharedPreferences sharedPreferences;

    public LocalProfileRepository(@NonNull Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    @Override
    public void loadOrCreateProfile(@NonNull Callback callback) {
        String uid = sharedPreferences.getString("uid", null);
        if (NullUtil.isNull(uid)) {
            uid = "local_" + DateTimeUtil.nowMillis();
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
        profile.mmr = sharedPreferences.getInt("mmr", 1000);
        profile.rankedWins = sharedPreferences.getInt("rankedWins", 0);
        profile.rankedLosses = sharedPreferences.getInt("rankedLosses", 0);
        profile.seasonId = sharedPreferences.getString("seasonId", java.time.LocalDate.now(java.time.ZoneOffset.UTC).getYear() + "-" + String.format("%02d", java.time.LocalDate.now(java.time.ZoneOffset.UTC).getMonthValue()));
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
                .putInt("mmr", profile.mmr)
                .putInt("rankedWins", profile.rankedWins)
                .putInt("rankedLosses", profile.rankedLosses)
                .putString("seasonId", profile.seasonId)
                .apply();
    }
}
