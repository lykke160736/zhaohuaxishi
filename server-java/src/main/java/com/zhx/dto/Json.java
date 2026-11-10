package com.zhx.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) return new LinkedHashMap<>();
        try {
            return MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static List<Map<String, Object>> parseList(String text) {
        if (text == null || text.isBlank()) return new java.util.ArrayList<>();
        try {
            return MAPPER.readValue(text, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
