package com.example.coreclash.battlepass.model;

import java.util.HashMap;
import java.util.Map;

public class BpLevel {
    public int level;
    public Reward freeReward;
    public Reward premiumReward;

    public static BpLevel fromMap(Map<String, Object> data) {
        BpLevel bpLevel = new BpLevel();
        bpLevel.level = valueAsInt(data.get("level"), 1);
        Object free = data.get("freeReward");
        Object premium = data.get("premiumReward");
        bpLevel.freeReward = free instanceof Map ? Reward.fromMap((Map<String, Object>) free) : null;
        bpLevel.premiumReward = premium instanceof Map ? Reward.fromMap((Map<String, Object>) premium) : null;
        return bpLevel;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("level", level);
        map.put("freeReward", freeReward == null ? null : freeReward.toMap());
        map.put("premiumReward", premiumReward == null ? null : premiumReward.toMap());
        return map;
    }

    private static int valueAsInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
