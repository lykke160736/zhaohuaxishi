package com.zhx.dto;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class Row {
    private Row() {
    }

    public static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception e) {
                return null;
            }
        }
        if (value instanceof byte[] bytes) return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return String.valueOf(value);
    }

    public static String str(Map<String, Object> row, String key, String def) {
        String value = str(row, key);
        return value == null ? def : value;
    }

    public static int i(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static long l(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    public static BigDecimal bd(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal d) return d;
        if (value instanceof Number n) return new BigDecimal(n.toString());
        return new BigDecimal(String.valueOf(value));
    }

    public static LocalDateTime dt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime dt) return dt;
        if (value instanceof java.util.Date d) return LocalDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC);
        if (value instanceof String s) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC);
            } catch (Exception ignored) {
                try {
                    return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
                } catch (Exception ignored2) {
                    return null;
                }
            }
        }
        return null;
    }

    public static String iso(Object value) {
        LocalDateTime dt = null;
        if (value instanceof LocalDateTime v) dt = v;
        else if (value instanceof Timestamp ts) dt = ts.toLocalDateTime();
        else if (value instanceof java.util.Date d) dt = LocalDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC);
        else if (value instanceof String s) {
            try {
                return ISO_MILLIS.format(OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC));
            } catch (Exception ignored) {
                return s;
            }
        }
        if (dt == null) return null;
        return ISO_MILLIS.format(dt.atZone(ZoneOffset.UTC));
    }

    public static String iso(Map<String, Object> row, String key) {
        return iso(row.get(key));
    }
}
