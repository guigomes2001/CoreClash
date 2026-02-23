package com.example.coreclash.battlepass.domain;

import androidx.annotation.NonNull;

import com.example.coreclash.battlepass.model.Season;

public class BpProgressCalculator {

    public int totalXpForLevel(@NonNull Season season, int targetLevel) {
        int safeLevel = Math.max(1, targetLevel);
        double raw = season.baseXpPerLevel * Math.pow(season.growthFactor, safeLevel - 1);
        return (int) Math.max(season.baseXpPerLevel, Math.round(raw));
    }

    public int levelFromXp(@NonNull Season season, int xp) {
        int safeXp = Math.max(0, xp);
        int level = 1;
        int cumulative = 0;

        while (level < season.maxLevel) {
            int required = totalXpForLevel(season, level);
            if (safeXp < cumulative + required) break;
            cumulative += required;
            level++;
        }
        return level;
    }

    public int xpIntoLevel(@NonNull Season season, int xp) {
        int level = levelFromXp(season, xp);
        int consumed = 0;
        for (int i = 1; i < level; i++) {
            consumed += totalXpForLevel(season, i);
        }
        return Math.max(0, xp - consumed);
    }
}
