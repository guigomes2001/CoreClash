package com.example.coreclash.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlayerProfile {
    public String uid;
    public String displayName;
    public long coins;
    public List<String> ownedThemes;
    public List<String> ownedSymbolStyles;
    public String equippedTheme;
    public String equippedSymbolStyle;

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
        return profile;
    }

    public boolean ownsTheme(String id) {
        return ownedThemes != null && ownedThemes.contains(id);
    }

    public boolean ownsSymbolStyle(String id) {
        return ownedSymbolStyles != null && ownedSymbolStyles.contains(id);
    }
}
