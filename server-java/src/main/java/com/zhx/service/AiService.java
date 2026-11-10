package com.zhx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhx.domain.AuthUser;
import com.zhx.dto.Json;
import com.zhx.dto.Money;
import com.zhx.dto.Row;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiService {
    public static final Map<String, List<String>> AI_QUERIES = Map.of(
            "customer", List.of("盲盒查询", "我的订单", "退款资格", "平台规则"),
            "merchant", List.of("本店库存", "本店订单", "经营统计", "本店售后", "平台规则"),
            "admin", List.of("入驻审核", "平台售后", "平台统计", "平台规则"));

    private static final List<String> RULES = List.of(
            "下单后最多3分钟支付，且不能超过取货截止。",
            "普通退款：已付款未领取、付款15分钟内，且当前时间早于取货截止前2小时，订单没有退款记录。",
            "支付成功按实付金额每满1元获得1积分；100积分兑换5元无门槛券。",
            "到店自提，不提供配送。成分和过敏原未提供时无法确认，请咨询门店。");

    private record Ticket(String accountId, long expires, List<Map<String, Object>> queries) {
    }

    private final DbRepository db;
    private final ServiceSupport support;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Value("${app.ai.base-url:http://127.0.0.1:8280}")
    private String gateway;

    public AiService(DbRepository db, ServiceSupport support) {
        this.db = db;
        this.support = support;
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(gateway + "/internal/status"))
                    .timeout(Duration.ofMillis(2000)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                out.put("configured", false);
                out.put("message", "智能服务暂不可用");
                return out;
            }
            Map<?, ?> data = mapper.readValue(response.body(), Map.class);
            boolean configured = Boolean.TRUE.equals(data.get("configured"))
                    || "true".equals(String.valueOf(data.get("configured")));
            out.put("configured", configured);
            out.put("message", configured ? "智能服务已连接" : "请先配置模型服务");
        } catch (Exception e) {
            out.put("configured", false);
            out.put("message", "智能服务尚未连接");
        }
        return out;
    }

    public Map<String, Object> chat(AuthUser me, Map<String, Object> body) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Object rawMessages = body.get("messages");
        if (rawMessages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> message = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        message.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    messages.add(message);
                }
            }
            int from = Math.max(0, messages.size() - 10);
            messages = new ArrayList<>(messages.subList(from, messages.size()));
        }
        if (!validMessages(messages)) throw new HttpError(503, "请输入有效的问题，每条消息不超过4000字");

        byte[] ticketBytes = new byte[32];
        random.nextBytes(ticketBytes);
        StringBuilder ticket = new StringBuilder(64);
        for (byte b : ticketBytes) ticket.append(String.format("%02x", b));
        Ticket entry = new Ticket(me.id(), System.currentTimeMillis() + 120000,
                java.util.Collections.synchronizedList(new ArrayList<>()));
        tickets.put(ticket.toString(), entry);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ticket", ticket.toString());
            payload.put("role", me.role());
            payload.put("queries", AI_QUERIES.get(me.role()));
            payload.put("messages", messages);
            HttpRequest request = HttpRequest.newBuilder(URI.create(gateway + "/internal/chat"))
                    .timeout(Duration.ofMillis(110000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpError(503, "智能服务暂不可用，请检查模型配置及服务状态");
            }
            Map<?, ?> data = mapper.readValue(response.body(), Map.class);
            Object text = data.get("text");
            if (text == null || String.valueOf(text).isBlank()) {
                throw new HttpError(503, "智能服务没有返回回答，请重试");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("text", String.valueOf(text));
            out.put("queries", new ArrayList<>(entry.queries()));
            return out;
        } catch (HttpError e) {
            throw e;
        } catch (Exception e) {
            throw new HttpError(503, "智能服务暂未连接或响应超时，请稍后重试");
        } finally {
            tickets.remove(ticket.toString());
        }
    }

    public Map<String, Object> tool(Map<String, Object> body) {
        try {
            Object ticketValue = body.get("ticket");
            Ticket entry = ticketValue == null ? null : tickets.get(String.valueOf(ticketValue));
            if (entry == null || entry.expires() < System.currentTimeMillis()) throw new HttpError(403, "查询授权已失效");
            AuthUser me = db.accountById(entry.accountId());
            if (me == null || !"active".equals(me.status())) throw HttpError.forbidden("账号不可用");
            String kind = String.valueOf(body.getOrDefault("kind", ""));
            Map<String, Object> filters = body.get("filters") instanceof Map<?, ?> map
                    ? castMap(map) : new LinkedHashMap<>();
            Map<String, Object> result = queryBusiness(me, kind, filters);
            entry.queries().add(Map.of("name", kind, "queriedAt", result.get("queriedAt")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("result", result);
            return out;
        } catch (HttpError e) {
            throw new HttpError(403, e.getMessage());
        } catch (Exception e) {
            throw new HttpError(403, e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    public Map<String, Object> queryBusiness(AuthUser me, String kind, Map<String, Object> filters) {
        List<String> allowed = AI_QUERIES.get(me.role());
        if (allowed == null || !allowed.contains(kind)) throw new HttpError(403, "当前身份无权使用该查询");
        long time = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", kind);
        result.put("queriedAt", support.iso(time));
        result.put("currency", "CNY");
        result.put("amountUnit", "分");

        if ("平台规则".equals(kind)) {
            result.put("rules", RULES);
            return result;
        }
        if ("盲盒查询".equals(kind)) {
            Object centerValue = support.getSetting("center:" + me.id(),
                    Map.of("lat", 24.551, "lng", 118.054));
            Map<String, Object> center = centerValue instanceof Map<?, ?> map ? castMap(map)
                    : new LinkedHashMap<>(Map.of("lat", 24.551, "lng", 118.054));
            Object radiusValue = filters.get("radiusKm");
            double radius = Math.min(15, Math.max(0, radiusValue == null ? 15 : support.numOrNaN(radiusValue)));
            Object priceValue = filters.get("maxPriceYuan");
            double maxPriceYuan = priceValue == null ? 100000 : support.numOrNaN(priceValue);
            double maxPrice = maxPriceYuan * 100;
            if (!Double.isFinite(radius) || !Double.isFinite(maxPrice) || maxPrice < 0) {
                throw new HttpError(403, "距离或预算无效");
            }
            double centerLat = support.numOrNaN(center.get("lat"));
            double centerLng = support.numOrNaN(center.get("lng"));
            String category = filters.get("category") == null ? "" : String.valueOf(filters.get("category"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> batch : db.aiBatches(new java.math.BigDecimal(String.valueOf(maxPrice)).movePointLeft(2))) {
                double lat = support.numOrNaN(Row.bd(batch, "lat"));
                double lng = support.numOrNaN(Row.bd(batch, "lng"));
                double a = Math.pow(Math.sin(Math.toRadians(lat - centerLat) / 2), 2)
                        + Math.cos(Math.toRadians(centerLat)) * Math.cos(Math.toRadians(lat))
                        * Math.pow(Math.sin(Math.toRadians(lng - centerLng) / 2), 2);
                double distance = Math.round(6371 * 2 * Math.asin(Math.sqrt(Math.min(1, a))) * 100) / 100.0;
                LocalDateTime pickupEnd = Row.dt(batch, "pickup_end");
                if (pickupEnd == null || pickupEnd.toInstant(ZoneOffset.UTC).toEpochMilli() <= time) continue;
                if (distance > radius) continue;
                if (!category.isEmpty() && !category.equals(Row.str(batch, "category"))) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", Row.str(batch, "id"));
                item.put("no", Row.str(batch, "no"));
                item.put("storeName", Row.str(batch, "store_name"));
                item.put("address", Row.str(batch, "address"));
                item.put("category", Row.str(batch, "category"));
                item.put("price", Money.cents(batch.get("price")));
                item.put("available", Row.i(batch, "total") - Row.i(batch, "sold") - Row.i(batch, "reserved"));
                item.put("pickupStart", Row.iso(batch, "pickup_start"));
                item.put("pickupEnd", Row.iso(batch, "pickup_end"));
                item.put("distanceKm", distance);
                item.put("path", "/batch/" + Row.str(batch, "id"));
                items.add(item);
            }
            String sort = filters.get("sort") == null ? "" : String.valueOf(filters.get("sort"));
            if ("price".equals(sort)) {
                items.sort(java.util.Comparator.comparingInt(x -> (Integer) x.get("price")));
            } else {
                items.sort(java.util.Comparator.comparingDouble(x -> (Double) x.get("distanceKm")));
            }
            result.put("total", items.size());
            result.put("items", items.subList(0, Math.min(20, items.size())));
            result.put("limit", 20);
            result.put("radiusKm", radius);
            return result;
        }
        if ("我的订单".equals(kind) || "退款资格".equals(kind)) {
            String orderNo = filters.get("orderNo") == null ? "" : String.valueOf(filters.get("orderNo"));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> order : db.ordersByUserDesc(me.id())) {
                if (orderNo.isEmpty() || orderNo.equals(Row.str(order, "no")) || orderNo.equals(Row.str(order, "id"))) {
                    rows.add(order);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> order : rows.subList(0, Math.min(20, rows.size()))) {
                Map<String, Object> snap = Json.parseObject(Row.str(order, "snap"));
                String reason = "";
                if (!"to_pickup".equals(Row.str(order, "status"))) {
                    reason = "当前状态不可申请普通退款";
                } else {
                    LocalDateTime paidAt = Row.dt(order, "paid_at");
                    long paidMillis = paidAt == null ? 0 : paidAt.toInstant(ZoneOffset.UTC).toEpochMilli();
                    LocalDateTime pickupEnd = support.parseTime(snap.get("pickupEnd"));
                    long pickupEndMillis = pickupEnd == null ? 0 : pickupEnd.toInstant(ZoneOffset.UTC).toEpochMilli();
                    if ((time - paidMillis) / 60000.0 > 15) reason = "付款已超过15分钟";
                    else if (time >= pickupEndMillis - 7200000L) reason = "已临近取货截止时间（2小时内）";
                    else if (db.refundExistsByOrder(Row.str(order, "id"))) reason = "订单已有退款记录";
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("no", Row.str(order, "no"));
                item.put("status", Row.str(order, "status"));
                item.put("amount", Money.cents(order.get("amount")));
                item.put("storeName", snap.get("storeName"));
                item.put("pickupStart", snap.get("pickupStart"));
                item.put("pickupEnd", snap.get("pickupEnd"));
                item.put("createdAt", Row.iso(order, "created_at"));
                item.put("refundable", reason.isEmpty());
                item.put("refundReason", reason.isEmpty() ? "当前满足普通退款条件，提交时会再次校验" : reason);
                item.put("path", "/order/" + Row.str(order, "id"));
                items.add(item);
            }
            result.put("total", rows.size());
            result.put("items", items);
            result.put("limit", 20);
            return result;
        }
        if (me.isMerchant()) {
            Map<String, Object> store = db.storeByOwner(me.id());
            if (store == null) {
                result.put("message", "尚无已入驻门店");
                result.put("items", List.of());
                return result;
            }
            String storeId = Row.str(store, "id");
            List<Map<String, Object>> orders = db.ordersByStore(storeId);
            List<Map<String, Object>> batches = db.batchesByStore(storeId);
            if ("本店库存".equals(kind)) {
                result.put("storeName", Row.str(store, "name"));
                List<Map<String, Object>> items = new ArrayList<>();
                for (Map<String, Object> batch : batches) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("no", Row.str(batch, "no"));
                    item.put("category", Row.str(batch, "category"));
                    item.put("price", Money.cents(batch.get("price")));
                    item.put("total", Row.i(batch, "total"));
                    item.put("sold", Row.i(batch, "sold"));
                    item.put("reserved", Row.i(batch, "reserved"));
                    item.put("available", Row.i(batch, "total") - Row.i(batch, "sold") - Row.i(batch, "reserved"));
                    item.put("pickupEnd", Row.iso(batch, "pickup_end"));
                    LocalDateTime pickupEnd = Row.dt(batch, "pickup_end");
                    String status = pickupEnd != null && time >= pickupEnd.toInstant(ZoneOffset.UTC).toEpochMilli()
                            ? "ended"
                            : ("on_sale".equals(Row.str(batch, "status"))
                            && Row.i(batch, "total") - Row.i(batch, "sold") - Row.i(batch, "reserved") <= 0
                            ? "sold_out" : Row.str(batch, "status"));
                    item.put("status", status);
                    items.add(item);
                }
                result.put("items", items);
                result.put("path", "/merchant/batches");
                return result;
            }
            if ("本店售后".equals(kind)) {
                List<Map<String, Object>> items = new ArrayList<>();
                for (Map<String, Object> row : db.aftersalesByStore(storeId)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", Row.str(row, "id"));
                    item.put("status", Row.str(row, "status"));
                    item.put("reason", Row.str(row, "reason"));
                    item.put("deadline", Row.iso(row, "deadline"));
                    items.add(item);
                }
                result.put("items", items);
                result.put("path", "/merchant/aftersale");
                return result;
            }
            String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            List<Map<String, Object>> todayOrders = new ArrayList<>();
            Map<String, Integer> allByStatus = new LinkedHashMap<>();
            Map<String, Integer> todayByStatus = new LinkedHashMap<>();
            int revenue = 0;
            for (Map<String, Object> order : orders) {
                String status = Row.str(order, "status");
                allByStatus.merge(status, 1, Integer::sum);
                LocalDateTime createdAt = Row.dt(order, "created_at");
                String createdDay = createdAt == null ? "" : createdAt.plusHours(8).toLocalDate().toString();
                if (today.equals(createdDay)) {
                    todayOrders.add(order);
                    todayByStatus.merge(status, 1, Integer::sum);
                }
                LocalDateTime paidAt = Row.dt(order, "paid_at");
                String paidDay = paidAt == null ? "" : paidAt.plusHours(8).toLocalDate().toString();
                if (paidAt != null && today.equals(paidDay)
                        && ("to_pickup".equals(status) || "picked".equals(status) || "no_show".equals(status))) {
                    revenue += Money.cents(order.get("amount"));
                }
            }
            List<Map<String, Object>> waiting = new ArrayList<>();
            for (Map<String, Object> order : orders) {
                if ("to_pickup".equals(Row.str(order, "status")) && waiting.size() < 30) waiting.add(rawOrder(order));
            }
            result.put("storeName", Row.str(store, "name"));
            result.put("timezone", "Asia/Shanghai");
            result.put("today", today);
            result.put("allOrderCount", orders.size());
            result.put("todayOrderCount", todayOrders.size());
            result.put("allByStatus", allByStatus);
            result.put("todayByStatus", todayByStatus);
            result.put("waitingOrders", waiting);
            result.put("revenueDefinition", "按付款日期统计当前待取货、已领取、超时未取订单的实付金额；不含退款中及已退款订单");
            result.put("todayRevenue", revenue);
            result.put("path", "/merchant/orders");
            return result;
        }
        if ("入驻审核".equals(kind)) {
            result.put("pendingCount", db.pendingApplicationsCount());
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : db.pendingApplications()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("store_name", Row.str(row, "store_name"));
                item.put("category", Row.str(row, "category"));
                item.put("submitted_at", Row.iso(row, "submitted_at"));
                items.add(item);
            }
            result.put("items", items);
            result.put("path", "/admin/audit");
            return result;
        }
        if ("平台售后".equals(kind)) {
            result.put("pendingCount", db.platformReviewCount());
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : db.platformReviewAftersales()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", Row.str(row, "id"));
                item.put("reason", Row.str(row, "reason"));
                item.put("status", Row.str(row, "status"));
                item.put("created_at", Row.iso(row, "created_at"));
                items.add(item);
            }
            result.put("items", items);
            result.put("path", "/admin/intervention");
            return result;
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Map<String, Object> row : db.accountGroupStats()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", Row.str(row, "role"));
            item.put("status", Row.str(row, "status"));
            item.put("count", Row.l(row, "count"));
            accounts.add(item);
        }
        List<Map<String, Object>> orderStats = new ArrayList<>();
        for (Map<String, Object> row : db.orderGroupStats()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", Row.str(row, "status"));
            item.put("count", Row.l(row, "count"));
            item.put("amount", Money.cents(row.get("amount")));
            orderStats.add(item);
        }
        result.put("accounts", accounts);
        result.put("stores", db.storeCount());
        result.put("orders", orderStats);
        result.put("pendingApplications", db.pendingApplicationsCount());
        result.put("pendingInterventions", db.platformReviewCount());
        result.put("path", "/admin");
        return result;
    }

    private Map<String, Object> rawOrder(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", Row.str(row, "id"));
        out.put("no", Row.str(row, "no"));
        out.put("user_id", Row.str(row, "user_id"));
        out.put("batch_id", Row.str(row, "batch_id"));
        out.put("store_id", Row.str(row, "store_id"));
        out.put("amount", Money.cents(row.get("amount")));
        out.put("original_amount", row.get("original_amount") == null ? null : Money.cents(row.get("original_amount")));
        out.put("discount", row.get("discount") == null ? null : Money.cents(row.get("discount")));
        out.put("coupon_id", Row.str(row, "coupon_id"));
        out.put("points_gained", Row.i(row, "points_gained"));
        out.put("status", Row.str(row, "status"));
        out.put("created_at", Row.iso(row, "created_at"));
        out.put("pay_deadline", Row.iso(row, "pay_deadline"));
        out.put("paid_at", Row.iso(row, "paid_at"));
        out.put("picked_at", Row.iso(row, "picked_at"));
        out.put("pickup_code", Row.str(row, "pickup_code"));
        out.put("qr_token", Row.str(row, "qr_token"));
        out.put("channel_tx", Row.str(row, "channel_tx"));
        out.put("request_key", Row.str(row, "request_key"));
        out.put("operator", Row.str(row, "operator"));
        out.put("cancel_reason", Row.str(row, "cancel_reason"));
        out.put("snap", Row.str(row, "snap"));
        return out;
    }

    private boolean validMessages(List<Map<String, Object>> messages) {
        if (messages.isEmpty()) return false;
        Map<String, Object> last = messages.get(messages.size() - 1);
        if (!"user".equals(last.get("role"))) return false;
        for (Map<String, Object> message : messages) {
            Object role = message.get("role");
            Object content = message.get("content");
            if (!"user".equals(role) && !"assistant".equals(role)) return false;
            if (!(content instanceof String text) || text.trim().isEmpty() || text.length() > 4000) return false;
        }
        return true;
    }

    private static Map<String, Object> castMap(Map<?, ?> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }
}
