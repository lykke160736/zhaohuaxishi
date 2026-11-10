package com.zhx.service;

import com.zhx.domain.AuthUser;
import com.zhx.dto.Json;
import com.zhx.dto.Row;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import com.zhx.repository.SettingsRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ServiceSupport {
    private static final DateTimeFormatter ISO_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final String RAND_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final DbRepository db;
    private final SettingsRepository settings;
    private final AtomicLong seq = new AtomicLong();

    public ServiceSupport(DbRepository db, SettingsRepository settings) {
        this.db = db;
        this.settings = settings;
    }

    public long now() {
        return System.currentTimeMillis();
    }

    public LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    public String iso(long epochMillis) {
        return ISO_MILLIS.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC));
    }

    public String isoNow() {
        return iso(now());
    }

    public String isoAddMinutes(int minutes) {
        return iso(now() + minutes * 60000L);
    }

    public String isoOf(LocalDateTime dt) {
        return Row.iso(dt);
    }

    public LocalDateTime dbTime(String text) {
        return parseTime(text);
    }

    public LocalDateTime parseTime(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            LocalDateTime local = LocalDateTime.parse(text);
            ZoneId zone = ZoneId.systemDefault();
            return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }
        return null;
    }

    public String uid(String prefix) {
        long t = now();
        long n = seq.incrementAndGet();
        long r = ThreadLocalRandom.current().nextInt(1296);
        return prefix + Long.toString(t, 36) + Long.toString(n, 36) + Long.toString(r, 36);
    }

    public long seq() {
        return seq.get();
    }

    public double numOrNaN(Object value) {
        if (value == null) return Double.NaN;
        if (value instanceof Number n) return n.doubleValue();
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) return Double.NaN;
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public boolean flag(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        if (value == null) return false;
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    public String randDigits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(ThreadLocalRandom.current().nextInt(10));
        return sb.toString();
    }

    public String randToken(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(RAND_CHARS.charAt(ThreadLocalRandom.current().nextInt(RAND_CHARS.length())));
        }
        return sb.toString();
    }

    public String roleName(String role) {
        return com.zhx.domain.Constants.ROLE_NAMES.getOrDefault(role, role);
    }

    public void log(String actor, String action, String detail) {
        db.insertLog(uid("L"), nowUtc(), actor, action, detail);
    }

    public void notify(String to, String title, String body, String link, String type) {
        if (to == null || to.isEmpty()) return;
        db.insertNotice(uid("N"), to, title, body, link, type == null ? "info" : type, nowUtc());
        db.trimNotices(to);
    }

    public void notify(String to, String title, String body, String link) {
        notify(to, title, body, link, "info");
    }

    public Map<String, Object> storeOf(String id) {
        return db.storeById(id);
    }

    public Map<String, Object> merchantStoreOf(AuthUser me) {
        Map<String, Object> store = db.storeByOwner(me.id());
        if (store == null) throw HttpError.bad("门店尚未通过审核，暂不可操作");
        return store;
    }

    public String batchStatus(Map<String, Object> batch) {
        if (batch == null) return null;
        LocalDateTime end = Row.dt(batch, "pickup_end");
        if (end != null && !nowUtc().isBefore(end)) return "ended";
        int total = Row.i(batch, "total");
        int sold = Row.i(batch, "sold");
        int reserved = Row.i(batch, "reserved");
        if ("on_sale".equals(Row.str(batch, "status")) && total - sold - reserved <= 0) return "sold_out";
        return Row.str(batch, "status");
    }

    public String makePickupCode() {
        for (int i = 0; i < 20; i++) {
            String code = randDigits(8);
            if (!db.pickupCodeExists(code)) return code;
        }
        String millis = String.valueOf(now());
        return millis.substring(millis.length() - 8);
    }

    public String newOrderNo() {
        LocalDate today = LocalDate.now();
        String ymd = String.format("%02d%02d%02d", today.getYear() % 100, today.getMonthValue(), today.getDayOfMonth());
        long n = db.orderCount() + 1;
        int rand = ThreadLocalRandom.current().nextInt(90) + 10;
        return "ZHX" + ymd + String.format("%03d", n) + rand;
    }

    public int earnPoints(String userId, int amountFen) {
        if (userId == null) return 0;
        int gained = amountFen / 100;
        if (db.walletByUser(userId) != null) db.addPoints(userId, gained);
        else db.insertWallet(userId, gained);
        return gained;
    }

    public int revokePoints(String userId, int points) {
        if (userId == null || points == 0) return 0;
        Map<String, Object> wallet = db.walletByUser(userId);
        if (wallet == null) return 0;
        int current = Row.i(wallet, "points");
        int revoke = Math.min(current, points);
        db.subtractPoints(userId, revoke);
        return revoke;
    }

    public void welcomeCustomer(AuthUser acc) {
        if (!acc.isCustomer()) return;
        if (db.walletExists(acc.id())) return;
        db.insertWallet(acc.id(), 100);
        db.insertCoupon(uid("CP"), acc.id(), 500, 0, nowUtc());
        notify(acc.id(), "新用户礼包已到账", "赠送 100 惜食积分 + 1 张 5 元无门槛券，下单支付时可直接抵扣", "/wallet");
    }

    public void checkPickupWindow(Object startValue, Object endValue) {
        if (startValue == null || endValue == null
                || String.valueOf(startValue).trim().isEmpty() || String.valueOf(endValue).trim().isEmpty()) {
            throw HttpError.bad("请完整填写自提开始与截止时间");
        }
        LocalDateTime start = parseTime(startValue);
        LocalDateTime end = parseTime(endValue);
        if (start == null || end == null) throw HttpError.bad("自提时间格式无效，请重新选择");
        if (!start.isBefore(end)) throw HttpError.bad("自提开始时间必须早于截止时间");
        if (!end.isAfter(nowUtc())) throw HttpError.bad("取货截止时间已过，请重新选择");
    }

    public String moderationReason(Map<String, Object> body) {
        String reason = body.get("reason") == null ? "" : String.valueOf(body.get("reason")).trim();
        if (reason.isEmpty() || reason.length() > 500) throw HttpError.bad("请填写处理原因（1—500字）");
        return reason;
    }

    public void assertRole(AuthUser me, String role) {
        if (me == null) throw HttpError.unauthorized("请先登录");
        if (!role.equals(me.role())) throw HttpError.forbidden("当前身份无权执行此操作");
    }

    public Object getSetting(String key, Object fallback) {
        String raw = settings.findValue(key);
        if (raw == null) return fallback;
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw, Object.class);
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            return raw;
        }
    }

    public void setSetting(String key, Object value) {
        settings.upsert(key, Json.write(value));
    }

    public Map<String, Object> body(Map<String, Object> body) {
        return body == null ? new LinkedHashMap<>() : body;
    }
}
