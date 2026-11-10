package com.zhx;

import com.zhx.dto.Json;
import com.zhx.repository.DbRepository;
import com.zhx.service.OrderService;
import com.zhx.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRegressionTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired DbRepository db;
    @Autowired OrderService orders;
    @Autowired RefundService refunds;
    @Autowired com.zhx.service.MerchantService merchants;

    @Test
    void directPublishCreatesPurchasableBatchWithoutUnpublishedDraft() throws Exception {
        String token = login("mailang", "merchant");
        int drafts = jdbc.queryForObject("SELECT COUNT(*) FROM drafts WHERE published=0", Integer.class);
        var result = post("/api/batches", token, Map.of("category", "面包", "price", "9.90", "qty", 8,
                "pickupStart", Instant.now().plusSeconds(60).toString(),
                "pickupEnd", Instant.now().plusSeconds(4 * 3600).toString()));
        assertEquals(200, result.status(), result.data().toString());
        assertEquals(990, result.data().path("batch").path("price").asInt());
        assertEquals("on_sale", result.data().path("batch").path("status").asText());
        assertEquals(drafts, jdbc.queryForObject("SELECT COUNT(*) FROM drafts WHERE published=0", Integer.class));
        String customer = login("chenxiaoman", "customer");
        String order = create(customer, result.data().path("batch").path("id").asText());
        var payment = post("/api/orders/" + order + "/pay", customer, Map.of());
        payments.confirmPayment(payment.data().path("payment").path("id").asText());
        assertEquals(new BigDecimal("9.90"), db.orderById(order).get("amount"));
        assertEquals(9, db.orderById(order).get("points_gained"));
    }

    @Test
    void failedDirectPublishRollsBackDraftAndRejectsCustomer() throws Exception {
        String token = login("mailang", "merchant");
        int drafts = jdbc.queryForObject("SELECT COUNT(*) FROM drafts", Integer.class);
        jdbc.update("UPDATE stores SET operation_status='suspended' WHERE id='S1'");
        var body = Map.<String, Object>of("category", "面包", "price", "9.90", "qty", 8,
                "pickupStart", Instant.now().plusSeconds(60).toString(),
                "pickupEnd", Instant.now().plusSeconds(4 * 3600).toString());
        assertEquals(400, post("/api/batches", token, body).status());
        assertEquals(drafts, jdbc.queryForObject("SELECT COUNT(*) FROM drafts", Integer.class));
        assertEquals(403, post("/api/batches", login("chenxiaoman", "customer"), body).status());
    }

    @Test
    void archivedBatchLeavesListingsButPreservesOrders() throws Exception {
        int orders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE batch_id='B1'", Integer.class);
        assertTrue(orders > 0);
        jdbc.update("UPDATE batches SET status='archived' WHERE id='B1'");
        assertNotNull(db.batchById("B1"));
        assertTrue(db.allBatches().stream().noneMatch(b -> "B1".equals(b.get("id"))));
        assertTrue(db.batchesByStore("S1").stream().noneMatch(b -> "B1".equals(b.get("id"))));
        assertEquals(orders, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE batch_id='B1'", Integer.class));
    }

    @Test
    void allManagementEndpointsRejectCustomersAndMerchants() throws Exception {
        for (String token : new String[]{login("chenxiaoman", "customer"), login("mailang", "merchant")}) {
            for (String path : new String[]{"/api/applications/AP1/audit", "/api/admin/stores/S1/operation",
                    "/api/admin/batches/B1/stop", "/api/accounts/u1/toggle", "/api/demo/refresh-window"}) {
                assertEquals(403, post(path, token, Map.of("reason", "审核", "status", "suspended")).status(), path);
            }
        }
        assertEquals("active", db.accountById("u1").status());
    }

    @Test
    void paymentAndRefundPreserveCentsAndOtherOrdersReservations() throws Exception {
        String token = login("chenxiaoman", "customer");
        jdbc.update("UPDATE batches SET price=12.90 WHERE id='B1'");
        String id = create(token, "B1");
        String userId = state(token).path("session").path("userId").asText();
        int points = jdbc.queryForObject("SELECT points FROM wallets WHERE user_id=?", Integer.class, userId);
        var pay = post("/api/orders/" + id + "/pay", token, Map.of());
        String intentId = pay.data().path("payment").path("id").asText();
        assertEquals(1290, pay.data().path("payment").path("amount").asInt());
        payments.confirmPayment(intentId);
        assertEquals(new BigDecimal("12.90"), db.orderById(id).get("amount"));
        assertEquals(points + 12, jdbc.queryForObject("SELECT points FROM wallets WHERE user_id=?", Integer.class, userId));
        // Another unpaid order must keep its own reservation through this refund.
        String waiting = create(token, "B1");
        int reserved = integer("reserved", "B1");
        int sold = integer("sold", "B1");
        assertEquals(200, post("/api/orders/" + id + "/refund", token, null).status());
        String refundId = jdbc.queryForObject("SELECT id FROM refunds WHERE order_id=?", String.class, id);
        assertEquals(new BigDecimal("12.90"), db.refundById(refundId).get("amount"));
        refunds.confirmRefund(refundId);
        refunds.confirmRefund(refundId);
        assertEquals(reserved, integer("reserved", "B1"));
        assertEquals(sold - 1, integer("sold", "B1"));
        assertEquals(points, jdbc.queryForObject("SELECT points FROM wallets WHERE user_id=?", Integer.class, userId));
        var secondPay = post("/api/orders/" + waiting + "/pay", token, Map.of());
        payments.confirmPayment(secondPay.data().path("payment").path("id").asText());
        assertEquals("to_pickup", db.orderById(waiting).get("status"));
    }

    @Test
    void couponKeepsExactDiscountedAmount() throws Exception {
        String token = login("chenxiaoman", "customer");
        String userId = state(token).path("session").path("userId").asText();
        jdbc.update("UPDATE batches SET price=12.90 WHERE id='B1'");
        db.insertCoupon("CP-regression", userId, 500, 0, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        String id = create(token, "B1");
        var payment = post("/api/orders/" + id + "/pay", token, Map.of("couponId", "CP-regression"));
        payments.confirmPayment(payment.data().path("payment").path("id").asText());
        var order = db.orderById(id);
        assertEquals(new BigDecimal("7.90"), order.get("amount"));
        assertEquals(new BigDecimal("12.90"), order.get("original_amount"));
        assertEquals(new BigDecimal("5.00"), order.get("discount"));
    }

    @Test
    void pickupTimeoutUsesOrderSnapshotAndKeepsStock() throws Exception {
        String token = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(token, 3 * 3600_000L);
        payments.confirmPayment(purchase.paymentId());
        var order = db.orderById(purchase.orderId());
        var snap = new LinkedHashMap<>(Json.parseObject(String.valueOf(order.get("snap"))));
        snap.put("pickupEnd", Instant.now().minusSeconds(1).toString());
        jdbc.update("UPDATE orders SET snap=? WHERE id=?", Json.write(snap), purchase.orderId());
        int sold = integer("sold", purchase.batchId());
        payments.scanTimeouts();
        payments.scanTimeouts();
        assertEquals("no_show", db.orderById(purchase.orderId()).get("status"));
        assertEquals(sold, integer("sold", purchase.batchId()));
    }

    @Test
    void concurrentCancelsReleaseOnlyTheirOwnReservation() throws Exception {
        String token = login("chenxiaoman", "customer");
        String id = create(token, "B1");
        create(token, "B1");
        int reserved = integer("reserved", "B1");
        var user = db.accountById(state(token).path("session").path("userId").asText());
        Callable<Boolean> cancel = () -> {
            try { orders.cancel(user, id, Map.of()); return true; }
            catch (com.zhx.exception.HttpError expected) { return false; }
        };
        assertEquals(1, race(cancel, cancel));
        assertEquals(reserved - 1, integer("reserved", "B1"));
    }

    @Test
    void paymentAndCancellationCannotBothConsumeReservation() throws Exception {
        String token = login("chenxiaoman", "customer");
        String id = create(token, "B1");
        var pay = post("/api/orders/" + id + "/pay", token, Map.of());
        String intent = pay.data().path("payment").path("id").asText();
        var user = db.accountById(state(token).path("session").path("userId").asText());
        int sold = integer("sold", "B1");
        int reserved = integer("reserved", "B1");
        race(() -> { payments.confirmPayment(intent); return true; }, () -> {
            try { orders.cancel(user, id, Map.of()); return true; }
            catch (com.zhx.exception.HttpError expected) { return false; }
        });
        String status = String.valueOf(db.orderById(id).get("status"));
        assertTrue(status.equals("closed") || status.equals("to_pickup"));
        assertEquals(sold + (status.equals("to_pickup") ? 1 : 0), integer("sold", "B1"));
        assertEquals(reserved - 1, integer("reserved", "B1"));
        assertEquals(status.equals("to_pickup") ? "paid" : "failed", db.intentById(intent).get("status"));
    }

    @Test
    void concurrentPaymentCallbacksSettleExactlyOnce() throws Exception {
        String token = login("chenxiaoman", "customer");
        Purchase purchase = buyAndPay(token, 3 * 3600_000L);
        int sold = integer("sold", purchase.batchId());
        int reserved = integer("reserved", purchase.batchId());
        race(() -> { payments.confirmPayment(purchase.paymentId()); return true; },
                () -> { payments.confirmPayment(purchase.paymentId()); return true; });
        assertEquals(sold + 1, integer("sold", purchase.batchId()));
        assertEquals(reserved - 1, integer("reserved", purchase.batchId()));
        assertEquals("paid", db.intentById(purchase.paymentId()).get("status"));
    }

    @Test
    void concurrentPickupRequestsOnlySucceedOnce() throws Exception {
        String token = login("chenxiaoman", "customer");
        String id = create(token, "B1");
        var pay = post("/api/orders/" + id + "/pay", token, Map.of());
        payments.confirmPayment(pay.data().path("payment").path("id").asText());
        var merchant = db.accountById("m1");
        String code = String.valueOf(db.orderById(id).get("pickup_code"));
        Callable<Boolean> verify = () -> {
            try { merchants.verify(merchant, Map.of("code", code)); return true; }
            catch (com.zhx.exception.HttpError expected) { return false; }
        };
        assertEquals(1, race(verify, verify));
        assertEquals("picked", db.orderById(id).get("status"));
    }

    private String create(String token, String batchId) throws Exception {
        var result = post("/api/orders", token, Map.of("batchId", batchId));
        assertEquals(200, result.status(), result.data().toString());
        return result.data().path("order").path("id").asText();
    }

    private int integer(String column, String batchId) {
        return jdbc.queryForObject("SELECT " + column + " FROM batches WHERE id=?", Integer.class, batchId);
    }

    private int race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> results = new java.util.ArrayList<>();
            for (var action : java.util.List.of(first, second)) {
                results.add(pool.submit(() -> { ready.countDown(); start.await(); return action.call(); }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            return successes;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
