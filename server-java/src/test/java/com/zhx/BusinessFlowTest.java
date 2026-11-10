package com.zhx;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对拍 tests/store.test.js 的 15 条业务规则。
 */
class BusinessFlowTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    private static final Map<String, String> CUSTOMER =
            Map.of("account", "chenxiaoman", "password", "123456", "role", "customer");
    private static final Map<String, String> OTHER =
            Map.of("account", "zhouguifang", "password", "123456", "role", "customer");

    @Test
    void loginRejectsWrongRoleAndPassword() throws Exception {
        Resp wrongRole = post("/api/auth/login", null, Map.of(
                "account", "chenxiaoman", "password", "123456", "role", "admin"));
        assertEquals(400, wrongRole.status());
        assertTrue(wrongRole.data().path("msg").asText().contains("身份"));

        Resp wrongPwd = post("/api/auth/login", null, Map.of(
                "account", "chenxiaoman", "password", "000000", "role", "customer"));
        assertEquals(400, wrongPwd.status());
    }

    @Test
    void passwordIsScryptAndNeverExposed() throws Exception {
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM accounts WHERE account='chenxiaoman'", String.class);
        assertNotNull(hash);
        assertTrue(hash.startsWith("scrypt$"));
        assertFalse(hash.contains("123456"));

        String token = login("chenxiaoman", "customer");
        Resp boot = get("/api/bootstrap", token);
        assertFalse(boot.data().toString().toLowerCase().contains("password"));

        Resp ai = get("/api/ai/status", null);
        assertEquals(200, ai.status());
        assertEquals("configured,message,ok", sortedKeys(ai.data()));
    }

    private String sortedKeys(JsonNode node) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        java.util.Collections.sort(keys);
        return String.join(",", keys);
    }

    @Test
    void roleCannotBeBypassedAndDisabledAccountLosesToken() throws Exception {
        String customer = login("chenxiaoman", "customer");
        assertEquals(403, post("/api/accounts/u1/toggle", customer, Map.of()).status());

        String admin = login("admin", "admin");
        assertEquals(200, post("/api/accounts/u1/toggle", admin, Map.of()).status());

        assertEquals(401, get("/api/bootstrap", customer).status());

        Resp again = post("/api/auth/login", null, CUSTOMER);
        assertEquals(400, again.status());
        assertTrue(again.data().path("msg").asText().contains("停用"));
    }

    @Test
    void orderOnlyReservesUntilPaymentConfirmed() throws Exception {
        String token = login("chenxiaoman", "customer");
        JsonNode before = state(token);
        JsonNode batch = pickBatch(before, 3600_000L);
        int total = batch.path("total").asInt();
        int sold = batch.path("sold").asInt();
        int reserved = batch.path("reserved").asInt();

        Resp created = post("/api/orders", token, Map.of("batchId", batch.path("id").asText()));
        assertEquals(200, created.status());
        JsonNode order = created.data().path("order");
        assertEquals("pending_payment", order.path("status").asText());
        assertTrue(order.path("pickupCode").isNull(), "未支付不能有取货码");

        JsonNode mid = state(token);
        JsonNode midBatch = find(mid.path("batches"), "id", batch.path("id").asText());
        assertEquals(reserved + 1, midBatch.path("reserved").asInt(), "下单后预留 +1");
        assertEquals(sold, midBatch.path("sold").asInt(), "支付前不成交");
        assertEquals(total, midBatch.path("total").asInt());

        String orderId = order.path("id").asText();
        Resp paid = post("/api/orders/" + orderId + "/pay", token, Map.of("channel", "wechat"));
        assertEquals(200, paid.status());
        JsonNode pendingState = state(token);
        assertEquals("pending_payment", find(pendingState.path("orders"), "id", orderId).path("status").asText());

        runJobs();

        JsonNode after = state(token);
        JsonNode finalOrder = find(after.path("orders"), "id", orderId);
        assertEquals("to_pickup", finalOrder.path("status").asText());
        assertTrue(finalOrder.path("pickupCode").asText().matches("\\d{8}"));
        assertFalse(finalOrder.path("channelTx").asText().isEmpty());
        JsonNode afterBatch = find(after.path("batches"), "id", batch.path("id").asText());
        assertEquals(sold + 1, afterBatch.path("sold").asInt(), "回调后成交 +1");
        assertEquals(reserved, afterBatch.path("reserved").asInt(), "回调后预留释放");
        assertEquals(total, afterBatch.path("total").asInt());

        int beforePoints = before.path("wallet").path(before.path("session").path("userId").asText())
                .path("points").asInt();
        int afterPoints = after.path("wallet").path(after.path("session").path("userId").asText())
                .path("points").asInt();
        assertTrue(afterPoints > beforePoints, "付款成交后发放积分");
    }

    @Test
    void repeatPayReusesSinglePendingIntent() throws Exception {
        String token = login("chenxiaoman", "customer");
        JsonNode batch = pickBatch(state(token), 3600_000L);
        Resp created = post("/api/orders", token, Map.of("batchId", batch.path("id").asText()));
        String orderId = created.data().path("order").path("id").asText();

        Resp a = post("/api/orders/" + orderId + "/pay", token, Map.of());
        Resp again = post("/api/orders/" + orderId + "/pay", token, Map.of());
        assertEquals(200, again.status());
        assertEquals(a.data().path("payment").path("id").asText(),
                again.data().path("payment").path("id").asText());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_intents WHERE order_id=?", Integer.class, orderId));
    }

    @Test
    void cancelReleasesReservedOnlyOnce() throws Exception {
        String token = login("chenxiaoman", "customer");
        JsonNode batch = pickBatch(state(token), 3600_000L);
        int reserved = batch.path("reserved").asInt();
        Resp created = post("/api/orders", token, Map.of("batchId", batch.path("id").asText()));
        String orderId = created.data().path("order").path("id").asText();

        assertEquals(200, post("/api/orders/" + orderId + "/cancel", token, null).status());
        assertEquals(400, post("/api/orders/" + orderId + "/cancel", token, null).status());

        JsonNode afterBatch = find(state(token).path("batches"), "id", batch.path("id").asText());
        assertEquals(reserved, afterBatch.path("reserved").asInt(), "预留回到原值");
    }

    @Test
    void otherCustomerCannotSeeOrOperateOrder() throws Exception {
        String mine = login("chenxiaoman", "customer");
        String other = login("zhouguifang", "customer");
        JsonNode batch = pickBatch(state(mine), 3600_000L);
        Resp created = post("/api/orders", mine, Map.of("batchId", batch.path("id").asText()));
        String orderId = created.data().path("order").path("id").asText();

        JsonNode otherState = state(other);
        assertFalse(containsId(otherState.path("orders"), orderId));

        for (String path : List.of("/pay", "/cancel", "/refund", "/aftersale")) {
            assertTrue(post("/api/orders/" + orderId + path, other, Map.of()).status() >= 400,
                    path + " 应拒绝越权操作");
        }
    }

    @Test
    void refundIsUniqueAndRestocksAfterChannelConfirm() throws Exception {
        String token = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(token, (long) (2.2 * 3600_000L));
        runJobs();

        assertEquals(200, post("/api/orders/" + purchase.orderId() + "/refund", token, null).status());
        assertEquals(400, post("/api/orders/" + purchase.orderId() + "/refund", token, null).status());

        JsonNode processing = state(token);
        assertEquals("processing", findRefund(processing, purchase.orderId()).path("status").asText());

        runJobs();

        JsonNode done = state(token);
        assertEquals("success", findRefund(done, purchase.orderId()).path("status").asText());
        assertEquals("refunded", find(done.path("orders"), "id", purchase.orderId()).path("status").asText());
        long count = 0;
        for (JsonNode refund : done.path("refunds")) {
            if (refund.path("orderId").asText().equals(purchase.orderId())) count++;
        }
        assertEquals(1, count);
    }

    @Test
    void verifyIsUniqueAndStoreScoped() throws Exception {
        String customer = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(customer, (long) (2.2 * 3600_000L));
        runJobs();
        String code = find(state(customer).path("orders"), "id", purchase.orderId())
                .path("pickupCode").asText();

        String otherStore = login("laozhang", "merchant");
        Resp denied = post("/api/verify", otherStore, Map.of("code", code));
        assertEquals(400, denied.status());
        assertTrue(denied.data().path("msg").asText().contains("非本店"));

        String merchant = login("mailang", "merchant");
        Resp ok = post("/api/verify", merchant, Map.of("code", code));
        assertEquals(200, ok.status(), String.valueOf(ok.data()));
        assertEquals("picked", ok.data().path("order").path("status").asText());

        Resp twice = post("/api/verify", merchant, Map.of("code", code));
        assertEquals(400, twice.status());
        assertTrue(twice.data().path("msg").asText().contains("重复核销"));
    }

    @Test
    void afterSaleAllowsSingleActiveRequestAndSingleRefund() throws Exception {
        String customer = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(customer, (long) (2.2 * 3600_000L));
        runJobs();

        assertEquals(200, post("/api/orders/" + purchase.orderId() + "/aftersale", customer,
                Map.of("reason", "到店无法交付")).status());
        assertEquals(400, post("/api/orders/" + purchase.orderId() + "/aftersale", customer,
                Map.of("reason", "再次申请")).status());

        String merchant = login("mailang", "merchant");
        JsonNode merchantState = state(merchant);
        JsonNode aftersale = find(merchantState.path("aftersales"), "orderId", purchase.orderId());
        String id = aftersale.path("id").asText();

        assertEquals(200, post("/api/aftersales/" + id + "/handle", merchant,
                Map.of("agree", true, "reply", "同意退款")).status());
        assertEquals(400, post("/api/aftersales/" + id + "/handle", merchant,
                Map.of("agree", true, "reply", "重复同意")).status());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE order_id=?", Integer.class, purchase.orderId()));
    }

    @Test
    void draftRejectsInvalidPickupWindows() throws Exception {
        String merchant = login("mailang", "merchant");
        Map<String, Object> base = Map.of("price", 12.9, "qty", 5, "category", "面包");
        List<Map<String, String>> badWindows = List.of(
                Map.of("pickupStart", "", "pickupEnd", ""),
                Map.of("pickupStart", "not-a-date", "pickupEnd", "not-a-date"),
                Map.of("pickupStart", iso(3), "pickupEnd", iso(2)),
                Map.of("pickupStart", iso(-1), "pickupEnd", iso(-0.5)));
        for (Map<String, String> bad : badWindows) {
            Map<String, Object> body = new java.util.LinkedHashMap<>(base);
            body.putAll(bad);
            assertEquals(400, post("/api/drafts", merchant, body).status(), String.valueOf(bad));
        }
        Map<String, Object> ok = new java.util.LinkedHashMap<>(base);
        ok.put("pickupStart", iso(-10.0 / 60));
        ok.put("pickupEnd", iso(1.5));
        assertEquals(200, post("/api/drafts", merchant, ok).status());
    }

    private String iso(double hoursFromNow) {
        return Instant.ofEpochMilli(System.currentTimeMillis() + (long) (hoursFromNow * 3600000L)).toString();
    }

    @Test
    void refreshWindowOnlyMovesOnSaleBatchesAndKeepsSnapshots() throws Exception {
        String customer = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(customer, (long) (2.2 * 3600_000L));
        runJobs();
        String snapEnd = find(state(customer).path("orders"), "id", purchase.orderId())
                .path("snap").path("pickupEnd").asText();

        jdbc.update("UPDATE batches SET pickup_end=? WHERE status='on_sale'",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        String admin = login("admin", "admin");
        Resp refreshed = post("/api/demo/refresh-window", admin, Map.of());
        assertEquals(200, refreshed.status());
        assertTrue(refreshed.data().path("changed").asInt() >= 1);

        assertEquals(snapEnd, find(state(customer).path("orders"), "id", purchase.orderId())
                .path("snap").path("pickupEnd").asText());
    }

    @Test
    void paymentTimeoutClosesOrderAndReleasesReservation() throws Exception {
        String token = login("chenxiaoman", "customer");
        JsonNode batch = pickBatch(state(token), 3600_000L);
        int reserved = batch.path("reserved").asInt();
        Resp created = post("/api/orders", token, Map.of("batchId", batch.path("id").asText()));
        String orderId = created.data().path("order").path("id").asText();

        jdbc.update("UPDATE orders SET pay_deadline=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), orderId);
        runJobs();

        JsonNode after = state(token);
        assertEquals("closed", find(after.path("orders"), "id", orderId).path("status").asText());
        assertEquals(reserved, find(after.path("batches"), "id", batch.path("id").asText())
                .path("reserved").asInt());
    }

    @Test
    void storeOperationGuardsNewBusinessButKeepsExistingOrders() throws Exception {
        String customer = login("chenxiaoman", "customer");
        String merchant = login("mailang", "merchant");
        String admin = login("admin", "admin");

        Map<String, Object> store = jdbc.queryForMap(
                "SELECT * FROM stores WHERE owner_id=(SELECT id FROM accounts WHERE account='mailang')");
        String storeId = String.valueOf(store.get("id"));
        String batchId = jdbc.queryForObject(
                "SELECT id FROM batches WHERE store_id=? AND status='on_sale' LIMIT 1",
                String.class, storeId);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC).plusHours(4);
        jdbc.update("UPDATE batches SET pickup_start=?,pickup_end=? WHERE id=?", start, end, batchId);

        JsonNode first = post("/api/orders", customer, Map.of("batchId", batchId)).data().path("order");
        JsonNode second = post("/api/orders", customer, Map.of("batchId", batchId)).data().path("order");
        Resp draft = post("/api/drafts", merchant, Map.of("price", 10, "qty", 5, "category", "面包",
                "pickupStart", start.toInstant(ZoneOffset.UTC).toString(),
                "pickupEnd", end.toInstant(ZoneOffset.UTC).toString()));
        assertEquals(200, draft.status(), String.valueOf(draft.data()));

        String path = "/api/admin/stores/" + storeId + "/operation";
        Map<String, Object> suspend = Map.of("status", "suspended", "reason", "资质复核，暂停新增经营");
        assertEquals(403, post(path, customer, suspend).status());
        assertEquals(403, post(path, merchant, suspend).status());
        assertEquals(400, post(path, admin, Map.of("status", "suspended", "reason", " ")).status());
        assertEquals(200, post(path, admin, suspend).status());
        assertEquals(400, post(path, admin, suspend).status());
        assertEquals(400, post("/api/orders", customer, Map.of("batchId", batchId)).status());
        assertEquals(400, post("/api/drafts/" + draft.data().path("id").asText() + "/publish", merchant, null).status());
        assertEquals(400, post("/api/batches/" + batchId + "/stock", merchant, Map.of("n", 1)).status());

        for (JsonNode order : List.of(first, second)) {
            assertEquals(200, post("/api/orders/" + order.path("id").asText() + "/pay", customer, null).status());
        }
        runJobs();
        String firstId = first.path("id").asText();
        String secondId = second.path("id").asText();
        Map<String, Object> paid = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", firstId);
        assertEquals("to_pickup", paid.get("status"));
        assertEquals(200, post("/api/verify", merchant,
                Map.of("code", String.valueOf(paid.get("pickup_code")))).status());
        assertEquals(200, post("/api/orders/" + secondId + "/refund", customer, null).status());
        runJobs();
        assertEquals("refunded", jdbc.queryForObject(
                "SELECT status FROM orders WHERE id=?", String.class, secondId));
        assertEquals(jdbc.queryForObject("SELECT snap FROM orders WHERE id=?", String.class, firstId),
                jdbc.queryForObject("SELECT snap FROM orders WHERE id=?", String.class, secondId));

        Map<String, Object> stopped = jdbc.queryForMap("SELECT * FROM stores WHERE id=?", storeId);
        assertEquals(suspend.get("reason"), stopped.get("operation_reason"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM logs WHERE action='暂停门店经营'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notices WHERE to_user=? AND title='暂停门店经营'",
                Integer.class, store.get("owner_id")));

        assertEquals(200, post(path, admin, Map.of("status", "active", "reason", "复核通过，恢复经营")).status());
        assertEquals(200, post("/api/orders", customer, Map.of("batchId", batchId)).status());
        assertEquals(200, post("/api/drafts/" + draft.data().path("id").asText() + "/publish", merchant, null).status());
    }

    @Test
    void adminStopBatchKeepsStockAndOrdersStoppedAfterStoreRestore() throws Exception {
        String customer = login("chenxiaoman", "customer");
        String merchant = login("mailang", "merchant");
        String admin = login("admin", "admin");
        Purchase purchase = buyAndPay(customer, (long) (2.2 * 3600_000L));
        runJobs();

        Map<String, Object> batch = jdbc.queryForMap("SELECT * FROM batches WHERE id=?", purchase.batchId());
        Map<String, Object> order = jdbc.queryForMap("SELECT * FROM orders WHERE id=?", purchase.orderId());
        String path = "/api/admin/batches/" + purchase.batchId() + "/stop";
        Map<String, Object> body = Map.of("reason", "商品描述不规范，整改后重新发布");

        assertEquals(403, post(path, merchant, body).status());
        assertEquals(403, post(path, customer, body).status());
        assertEquals(400, post(path, admin, Map.of("reason", "")).status());
        assertEquals(200, post(path, admin, body).status());
        assertEquals(400, post(path, admin, body).status());

        Map<String, Object> after = jdbc.queryForMap("SELECT * FROM batches WHERE id=?", purchase.batchId());
        assertEquals(batch.get("total"), after.get("total"));
        assertEquals(batch.get("sold"), after.get("sold"));
        assertEquals(batch.get("reserved"), after.get("reserved"));
        assertEquals(order.get("status"), jdbc.queryForMap(
                "SELECT * FROM orders WHERE id=?", purchase.orderId()).get("status"));

        assertEquals(400, post("/api/orders", customer, Map.of("batchId", purchase.batchId())).status());
        assertEquals(400, post("/api/batches/" + purchase.batchId() + "/stock", merchant, Map.of("n", 1)).status());

        for (String status : List.of("suspended", "active")) {
            assertEquals(200, post("/api/admin/stores/" + batch.get("store_id") + "/operation", admin,
                    Map.of("status", status, "reason", "复核处置")).status());
        }
        post("/api/demo/refresh-window", admin, Map.of());
        assertEquals("stopped", jdbc.queryForObject(
                "SELECT status FROM batches WHERE id=?", String.class, purchase.batchId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM logs WHERE action='平台下架批次'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notices WHERE title='盲盒批次已被平台下架'", Integer.class));
        assertEquals(200, post("/api/orders/" + purchase.orderId() + "/refund", customer, null).status());
    }

    private JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.path(field).asText())) return node;
        }
        throw new AssertionError("未找到 " + field + "=" + value);
    }

    private boolean containsId(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.path("id").asText())) return true;
        }
        return false;
    }

    private JsonNode findRefund(JsonNode state, String orderId) {
        for (JsonNode refund : state.path("refunds")) {
            if (orderId.equals(refund.path("orderId").asText())) return refund;
        }
        throw new AssertionError("未找到退款 " + orderId);
    }
}
