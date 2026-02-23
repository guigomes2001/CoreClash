package com.example.coreclash.battlepass.model;

import java.util.HashMap;
import java.util.Map;

public class Reward {
    public String id;
    public String type;
    public String itemId;
    public int amount;

    public static Reward fromMap(Map<String, Object> data) {
        Reward reward = new Reward();
        reward.id = valueAsString(data.get("id"), "");
        reward.type = valueAsString(data.get("type"), "currency");
        reward.itemId = valueAsString(data.get("itemId"), "");
        reward.amount = valueAsInt(data.get("amount"), 0);
        return reward;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("itemId", itemId);
        map.put("amount", amount);
        return map;
    }

    private static String valueAsString(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static int valueAsInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
