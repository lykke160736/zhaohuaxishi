package com.zhx.domain;

import java.util.List;
import java.util.Map;

public final class Constants {
    private Constants() {
    }

    public static final int PAY_MINUTES = 3;
    public static final List<String> CATEGORIES = List.of(
            "全部", "面包", "炸鸡", "自助餐", "糕点", "便当", "轻食", "水果", "饮品");

    public static final Map<String, String> ROLE_NAMES = Map.of(
            "customer", "顾客",
            "merchant", "商家",
            "admin", "平台管理员");

    public static final Map<String, Object> DEFAULT_CENTER = Map.of(
            "name", "衡中教育综合体",
            "city", "厦门市",
            "lat", 24.551,
            "lng", 118.054,
            "source", "default");
}
