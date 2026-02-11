package manager;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public class HomeAwayManager {

    private static final String PREFS = "home_away_balance";

    private final SharedPreferences prefs;

    public HomeAwayManager(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean chooseHome(@NonNull String playerA, @NonNull String playerB) {
        String keyA = key(playerA, playerB, "A");
        String keyB = key(playerA, playerB, "B");

        int aCount = prefs.getInt(keyA, 0);
        int bCount = prefs.getInt(keyB, 0);

        int diff = Math.max(-3, Math.min(3, aCount - bCount));
        double chanceAHome = clamp(0.35, 0.65, 0.5 - (diff * 0.1));

        boolean aHome = Math.random() < chanceAHome;

        if (aHome) aCount++; else bCount++;
        prefs.edit().putInt(keyA, aCount).putInt(keyB, bCount).apply();

        return aHome;
    }

    private double clamp(double min, double max, double v) {
        return Math.max(min, Math.min(max, v));
    }

    @NonNull
    private String key(@NonNull String a, @NonNull String b, @NonNull String side) {
        String p1 = a.compareToIgnoreCase(b) <= 0 ? a : b;
        String p2 = a.compareToIgnoreCase(b) <= 0 ? b : a;
        return "pair_" + p1 + "_" + p2 + "_" + side;
    }
}
