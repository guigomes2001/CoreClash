package manager;

import androidx.annotation.NonNull;

import com.example.coreclash.model.PlayerProfile;

import java.time.LocalDate;
import java.time.ZoneOffset;

import util.NullUtil;

public final class RankedSeasonManager {
    public static final int DEFAULT_MMR = 1000;
    public static final String EXCLUSIVE_STYLE_MYTHIC = "MYTHIC";

    public enum Tier {
        BRONZE,
        SILVER,
        GOLD
    }

    @NonNull
    public String currentSeasonId() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return now.getYear() + "-" + String.format("%02d", now.getMonthValue());
    }

    public void ensureProfileRankFields(@NonNull PlayerProfile profile) {
        if (profile.mmr <= 0) profile.mmr = DEFAULT_MMR;
        if (profile.rankedWins < 0) profile.rankedWins = 0;
        if (profile.rankedLosses < 0) profile.rankedLosses = 0;
        if (NullUtil.isNull(profile.seasonId) || profile.seasonId.trim().isEmpty()) {
            profile.seasonId = currentSeasonId();
        }
    }

    public boolean rolloverSeasonIfNeeded(@NonNull PlayerProfile profile) {
        ensureProfileRankFields(profile);
        String current = currentSeasonId();
        if (current.equals(profile.seasonId)) {
            return false;
        }

        if (profile.rankedPassActive && tierFor(profile.mmr) == Tier.GOLD && !profile.ownsSymbolStyle(EXCLUSIVE_STYLE_MYTHIC)) {
            profile.ownedSymbolStyles.add(EXCLUSIVE_STYLE_MYTHIC);
        }

        profile.seasonId = current;
        profile.mmr = DEFAULT_MMR;
        profile.rankedWins = 0;
        profile.rankedLosses = 0;
        profile.rankedPassActive = false;
        return true;
    }

    public int applyMatchResult(@NonNull PlayerProfile profile, boolean won) {
        ensureProfileRankFields(profile);

        int expected = 0;
        int delta = won ? 26 : -22;

        if (profile.mmr >= 1300) {
            delta = won ? 22 : -25;
        } else if (profile.mmr <= 900) {
            delta = won ? 30 : -18;
        }

        expected += delta;
        profile.mmr = Math.max(600, Math.min(2200, profile.mmr + expected));
        if (won) profile.rankedWins++;
        else profile.rankedLosses++;
        return expected;
    }

    @NonNull
    public Tier tierFor(int mmr) {
        if (mmr >= 1300) return Tier.GOLD;
        if (mmr >= 1100) return Tier.SILVER;
        return Tier.BRONZE;
    }
}
