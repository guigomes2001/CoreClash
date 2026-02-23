package com.example.coreclash.battlepass.model;

import java.util.HashMap;
import java.util.Map;

public class Mission {
    public String id;
    public String type;
    public String bucket;
    public String metric;
    public int goal;
    public int xpReward;
    public boolean active;

    public static Mission fromMap(String id, Map<String, Object> data) {
        Mission mission = new Mission();
        mission.id = id;
        mission.type = valueAsString(data.get("type"), "counter");
        mission.bucket = valueAsString(data.get("bucket"), "daily");
        mission.metric = valueAsString(data.get("metric"), "matches_played");
        mission.goal = valueAsInt(data.get("goal"), 1);
        mission.xpReward = valueAsInt(data.get("xpReward"), 50);
        mission.active = valueAsBoolean(data.get("active"), true);
        return mission;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("bucket", bucket);
        map.put("metric", metric);
        map.put("goal", goal);
        map.put("xpReward", xpReward);
        map.put("active", active);
        return map;
    }

    private static String valueAsString(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static int valueAsInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean valueAsBoolean(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
