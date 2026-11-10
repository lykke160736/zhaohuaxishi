package com.zhx.dto;

import com.zhx.domain.AuthUser;
import com.zhx.domain.DailyPlans;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StateMapper {
    private StateMapper() {
    }

    private static Integer centsOrNull(Object value) {
        return value == null ? null : Money.cents(value);
    }

    private static java.math.BigDecimal decimal(Map<String, Object> r, String key) {
        java.math.BigDecimal value = Row.bd(r, key);
        return value == null ? null : value.stripTrailingZeros();
    }

    public static Map<String, Object> store(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("name", Row.str(r, "name"));
        out.put("category", Row.str(r, "category"));
        out.put("address", Row.str(r, "address"));
        out.put("lat", decimal(r, "lat"));
        out.put("lng", decimal(r, "lng"));
        out.put("phone", Row.str(r, "phone"));
        out.put("notice", Row.str(r, "notice"));
        out.put("ownerId", Row.str(r, "owner_id"));
        out.put("auditStatus", Row.str(r, "audit_status"));
        out.put("operationStatus", Row.str(r, "operation_status"));
        out.put("operationReason", Row.str(r, "operation_reason"));
        out.put("operationAt", Row.iso(r, "operation_at"));
        out.put("dailyPlan", DailyPlans.asMap(Row.str(r, "id")));
        return out;
    }

    public static Map<String, Object> batch(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("no", Row.str(r, "no"));
        out.put("storeId", Row.str(r, "store_id"));
        out.put("category", Row.str(r, "category"));
        out.put("price", Money.cents(r.get("price")));
        out.put("pickupStart", Row.iso(r, "pickup_start"));
        out.put("pickupEnd", Row.iso(r, "pickup_end"));
        out.put("total", Row.i(r, "total"));
        out.put("sold", Row.i(r, "sold"));
        out.put("reserved", Row.i(r, "reserved"));
        out.put("status", Row.str(r, "status"));
        out.put("publishedAt", Row.iso(r, "published_at"));
        out.put("stopReason", Row.str(r, "stop_reason"));
        out.put("stoppedAt", Row.iso(r, "stopped_at"));
        out.put("stockLogs", Json.parseList(Row.str(r, "stock_logs")));
        return out;
    }

    public static Map<String, Object> order(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("no", Row.str(r, "no"));
        out.put("userId", Row.str(r, "user_id"));
        out.put("batchId", Row.str(r, "batch_id"));
        out.put("storeId", Row.str(r, "store_id"));
        out.put("amount", Money.cents(r.get("amount")));
        out.put("originalAmount", centsOrNull(r.get("original_amount")));
        out.put("discount", centsOrNull(r.get("discount")));
        out.put("couponId", Row.str(r, "coupon_id"));
        out.put("pointsGained", Row.i(r, "points_gained"));
        out.put("status", Row.str(r, "status"));
        out.put("createdAt", Row.iso(r, "created_at"));
        out.put("payDeadline", Row.iso(r, "pay_deadline"));
        out.put("paidAt", Row.iso(r, "paid_at"));
        out.put("pickedAt", Row.iso(r, "picked_at"));
        out.put("pickupCode", Row.str(r, "pickup_code"));
        out.put("qrToken", Row.str(r, "qr_token"));
        out.put("channelTx", Row.str(r, "channel_tx"));
        out.put("requestKey", Row.str(r, "request_key"));
        out.put("operator", Row.str(r, "operator"));
        out.put("cancelReason", Row.str(r, "cancel_reason"));
        out.put("snap", Json.parseObject(Row.str(r, "snap")));
        return out;
    }

    public static Map<String, Object> refund(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("orderId", Row.str(r, "order_id"));
        out.put("type", Row.str(r, "type"));
        out.put("amount", Money.cents(r.get("amount")));
        out.put("status", Row.str(r, "status"));
        out.put("restock", Row.bool(r, "restock"));
        out.put("reason", Row.str(r, "reason"));
        out.put("createdAt", Row.iso(r, "created_at"));
        out.put("confirmedAt", Row.iso(r, "confirmed_at"));
        return out;
    }

    public static Map<String, Object> aftersale(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("orderId", Row.str(r, "order_id"));
        out.put("reason", Row.str(r, "reason"));
        out.put("evidence", Row.str(r, "evidence"));
        out.put("status", Row.str(r, "status"));
        out.put("createdAt", Row.iso(r, "created_at"));
        out.put("deadline", Row.iso(r, "deadline"));
        out.put("reply", Row.str(r, "reply"));
        out.put("platformNote", Row.str(r, "platform_note"));
        out.put("handledAt", Row.iso(r, "handled_at"));
        out.put("resolvedAt", Row.iso(r, "resolved_at"));
        return out;
    }

    public static Map<String, Object> review(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("orderId", Row.str(r, "order_id"));
        out.put("storeId", Row.str(r, "store_id"));
        out.put("userId", Row.str(r, "user_id"));
        out.put("userName", Row.str(r, "user_name"));
        out.put("rating", Row.i(r, "rating"));
        out.put("text", Row.str(r, "text"));
        out.put("createdAt", Row.iso(r, "created_at"));
        return out;
    }

    public static Map<String, Object> notice(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("to", Row.str(r, "to_user"));
        out.put("title", Row.str(r, "title"));
        out.put("body", Row.str(r, "body"));
        out.put("link", Row.str(r, "link"));
        out.put("type", Row.str(r, "type"));
        out.put("read", Row.bool(r, "read"));
        out.put("createdAt", Row.iso(r, "created_at"));
        return out;
    }

    public static Map<String, Object> draft(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("storeId", Row.str(r, "store_id"));
        out.put("price", Money.cents(r.get("price")));
        out.put("qty", Row.i(r, "qty"));
        out.put("category", Row.str(r, "category"));
        out.put("pickupStart", Row.iso(r, "pickup_start"));
        out.put("pickupEnd", Row.iso(r, "pickup_end"));
        out.put("published", Row.bool(r, "published"));
        out.put("updatedAt", Row.iso(r, "updated_at"));
        return out;
    }

    public static Map<String, Object> application(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("storeName", Row.str(r, "store_name"));
        out.put("category", Row.str(r, "category"));
        out.put("applicant", Row.str(r, "applicant"));
        out.put("accountId", Row.str(r, "account_id"));
        out.put("address", Row.str(r, "address"));
        out.put("lat", decimal(r, "lat"));
        out.put("lng", decimal(r, "lng"));
        out.put("phone", Row.str(r, "phone"));
        out.put("materials", Row.str(r, "materials"));
        out.put("submittedAt", Row.iso(r, "submitted_at"));
        out.put("status", Row.str(r, "status"));
        out.put("note", Row.str(r, "note"));
        out.put("auditedAt", Row.iso(r, "audited_at"));
        return out;
    }

    public static Map<String, Object> coupon(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("amount", Money.cents(r.get("amount")));
        out.put("minSpend", Money.cents(r.get("min_spend")));
        out.put("used", Row.bool(r, "used"));
        out.put("createdAt", Row.iso(r, "created_at"));
        out.put("usedAt", Row.iso(r, "used_at"));
        out.put("orderId", Row.str(r, "order_id"));
        return out;
    }

    public static Map<String, Object> log(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("time", Row.iso(r, "time"));
        out.put("actor", Row.str(r, "actor"));
        out.put("action", Row.str(r, "action"));
        out.put("detail", Row.str(r, "detail"));
        return out;
    }

    public static Map<String, Object> account(Map<String, Object> r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(r, "id"));
        out.put("name", Row.str(r, "name"));
        out.put("account", Row.str(r, "account"));
        out.put("mobile", Row.str(r, "mobile"));
        out.put("phone", Row.str(r, "phone"));
        out.put("role", Row.str(r, "role"));
        out.put("status", Row.str(r, "status"));
        out.put("createdAt", Row.iso(r, "created_at"));
        return out;
    }

    public static Map<String, Object> account(AuthUser me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", me.id());
        out.put("name", me.name());
        out.put("account", me.account());
        out.put("mobile", me.mobile());
        out.put("phone", me.phone());
        out.put("role", me.role());
        out.put("status", me.status());
        out.put("createdAt", Row.iso(me.createdAt()));
        return out;
    }
}
