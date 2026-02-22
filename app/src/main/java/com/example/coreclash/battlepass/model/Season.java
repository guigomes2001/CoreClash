package com.example.coreclash.battlepass.model;

import com.google.firebase.Timestamp;

import java.util.HashMap;
import java.util.Map;

public class Season {
    public String id;
    public Timestamp startAt;
    public Timestamp endAt;
    public String status;
    public int baseXpPerLevel;
    public float growthFactor;
    public int maxLevel;

    public static Season fromMap(String id, Map<String, Object> data) {
        Season season = new Season();
        season.id = id;
        season.startAt = (Timestamp) data.get("startAt");
        season.endAt = (Timestamp) data.get("endAt");
        season.status = valueAsString(data.get("status"), "inactive");
        season.baseXpPerLevel = valueAsInt(data.get("baseXpPerLevel"), 120);
        season.growthFactor = valueAsFloat(data.get("growthFactor"), 1.2f);
        season.maxLevel = valueAsInt(data.get("maxLevel"), 50);
        return season;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("startAt", startAt);
        map.put("endAt", endAt);
        map.put("status", status);
        map.put("baseXpPerLevel", baseXpPerLevel);
        map.put("growthFactor", growthFactor);
        map.put("maxLevel", maxLevel);
        return map;
    }

    private static String valueAsString(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static int valueAsInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static float valueAsFloat(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }
}
