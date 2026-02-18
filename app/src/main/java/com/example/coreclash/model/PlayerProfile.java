package com.example.coreclash.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import util.NullUtil;

public class PlayerProfile {
    public String uid;
    public String displayName;
    public long coins;
    public List<String> ownedThemes;
    public List<String> ownedSymbolStyles;
    public String equippedTheme;
    public String equippedSymbolStyle;
    public int mmr;
    public int rankedWins;
    public int rankedLosses;
    public String seasonId;
    public boolean rankedPassActive;

    public PlayerProfile() {

    }

    public static PlayerProfile createDefault(String uid) {
        PlayerProfile profile = new PlayerProfile();
        profile.uid = uid;
        profile.displayName = "Player" + uid.substring(0, Math.min(5, uid.length()));
        profile.coins = 120;
        profile.ownedThemes = new ArrayList<>(Arrays.asList("ARENA"));
        profile.ownedSymbolStyles = new ArrayList<>(Arrays.asList("CLASSIC"));
        profile.equippedTheme = "ARENA";
        profile.equippedSymbolStyle = "CLASSIC";
        profile.mmr = 1000;
        profile.rankedWins = 0;
        profile.rankedLosses = 0;
        profile.seasonId = java.time.LocalDate.now(java.time.ZoneOffset.UTC).getYear() + "-" + String.format("%02d", java.time.LocalDate.now(java.time.ZoneOffset.UTC).getMonthValue());
        profile.rankedPassActive = false;
        return profile;
    }

    public boolean ownsTheme(String id) {
        return !NullUtil.isNull(ownedThemes) && ownedThemes.contains(id);
    }

    public boolean ownsSymbolStyle(String id) {
        return !NullUtil.isNull(ownedSymbolStyles) && ownedSymbolStyles.contains(id);
    }
}
