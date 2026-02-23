package com.example.coreclash.battlepass.domain;

import androidx.annotation.NonNull;

import com.example.coreclash.battlepass.model.BpState;
import com.example.coreclash.battlepass.model.Mission;

import java.util.List;
import java.util.Map;

public class MissionEngine {

    public static class MatchFinishedEvent {
        public final boolean won;
        public final boolean online;
        public final int winLines;

        public MatchFinishedEvent(boolean won, boolean online, int winLines) {
            this.won = won;
            this.online = online;
            this.winLines = Math.max(1, winLines);
        }
    }

    public int applyMatchResult(@NonNull BpState state,
                                @NonNull List<Mission> missions,
                                @NonNull MatchFinishedEvent event) {
        int grantedXp = event.won ? 45 : 25;
        if (event.online) grantedXp += 20;
        grantedXp += Math.min(20, event.winLines * 5);

        for (Mission mission : missions) {
            if (!mission.active || state.completedMissions.contains(mission.id)) continue;
            if (!matchesMission(mission.metric, event)) continue;

            int progress = state.missionProgress.getOrDefault(mission.id, 0) + metricDelta(mission.metric, event);
            int capped = Math.min(progress, mission.goal);
            state.missionProgress.put(mission.id, capped);

            if (capped >= mission.goal) {
                state.completedMissions.add(mission.id);
                grantedXp += mission.xpReward;
            }
        }

        state.xp += grantedXp;
        return grantedXp;
    }

    private boolean matchesMission(@NonNull String metric, @NonNull MatchFinishedEvent event) {
        return switch (metric) {
            case "matches_played" -> true;
            case "matches_won" -> event.won;
            case "online_matches" -> event.online;
            case "round_lines" -> event.winLines > 0;
            default -> false;
        };
    }

    private int metricDelta(@NonNull String metric, @NonNull MatchFinishedEvent event) {
        return switch (metric) {
            case "round_lines" -> event.winLines;
            default -> 1;
        };
    }
}
