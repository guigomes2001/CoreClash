package com.example.coreclash.battlepass.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BpState {
    public String userId;
    public String seasonId;
    public int xp;
    public int level;
    public boolean premiumOwned;
    public Set<String> claimedRewardIds = new HashSet<>();
    public Map<String, Integer> missionProgress = new HashMap<>();
    public Set<String> completedMissions = new HashSet<>();

    public static BpState empty(String userId, String seasonId) {
        BpState state = new BpState();
        state.userId = userId;
        state.seasonId = seasonId;
        state.xp = 0;
        state.level = 1;
        state.premiumOwned = false;
        return state;
    }
}
