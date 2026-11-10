package com.zhx.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DailyPlans {
    private DailyPlans() {
    }

    public record Plan(String publishTime, String pickupEnd, int price, int quantity) {
    }

    public static final Map<String, Plan> PLANS = new LinkedHashMap<>();

    static {
        plan("S1", "20:00", "23:00", 1290, 8);
        plan("S2", "19:00", "21:00", 1590, 10);
        plan("S3", "19:30", "21:30", 2590, 8);
        plan("S4", "17:30", "20:30", 990, 8);
        plan("S5", "20:30", "22:30", 1390, 8);
        plan("S6", "17:45", "20:45", 1290, 10);
        plan("S7", "13:00", "15:00", 1190, 10);
        plan("S8", "18:00", "21:00", 890, 8);
        plan("S9", "13:30", "15:30", 2290, 8);
        plan("S10", "19:15", "21:15", 1290, 10);
        plan("S11", "18:30", "21:30", 1090, 8);
        plan("S12", "13:15", "15:15", 1890, 8);
        plan("S13", "14:00", "17:00", 1390, 10);
        plan("S14", "20:30", "23:00", 690, 10);
        plan("S15", "10:30", "12:30", 790, 8);
        plan("S16", "10:00", "12:00", 890, 12);
        plan("S17", "20:00", "22:00", 1690, 8);
        plan("S18", "14:00", "16:00", 1090, 10);
        plan("S19", "14:30", "17:30", 1490, 8);
    }

    private static void plan(String id, String publishTime, String pickupEnd, int price, int quantity) {
        PLANS.put(id, new Plan(publishTime, pickupEnd, price, quantity));
    }

    public static Map<String, Object> asMap(String storeId) {
        Plan plan = PLANS.get(storeId);
        if (plan == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publishTime", plan.publishTime());
        out.put("pickupEnd", plan.pickupEnd());
        out.put("price", plan.price());
        out.put("quantity", plan.quantity());
        return out;
    }
}
