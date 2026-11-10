package com.zhx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhx.domain.AuthUser;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import com.zhx.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 对拍 tests/ai.test.js 的角色边界、退款资格与票据生命周期。 */
class AiToolsTest extends ApiTestBase {

    @Autowired
    AiService ai;

    @Autowired
    DbRepository db;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    private AuthUser account(String name) {
        return db.accountByAccountOrMobile(name);
    }

    @Test
    void aiQueriesRespectRoleAccountAndStoreBoundariesWithHardBudget() {
        AuthUser customer = account("chenxiaoman");
        AuthUser merchant = account("mailang");
        AuthUser admin = account("admin");

        HttpError roleError = assertThrows(HttpError.class,
                () -> ai.queryBusiness(customer, "平台统计", Map.of()));
        assertTrue(roleError.getMessage().contains("无权"));
        assertThrows(HttpError.class, () -> ai.queryBusiness(merchant, "我的订单", Map.of()));

        assertEquals(0, ai.queryBusiness(customer, "盲盒查询", Map.of("maxPriceYuan", 0)).get("total"));

        Set<String> ownNos = new HashSet<>(jdbc.queryForList(
                "SELECT no FROM orders WHERE user_id=?", String.class, customer.id()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) ai.queryBusiness(
                customer, "我的订单", Map.of("userId", admin.id())).get("items");
        assertTrue(orders.stream().allMatch(o -> ownNos.contains(o.get("no"))));

        String storeId = jdbc.queryForObject(
                "SELECT id FROM stores WHERE owner_id=?", String.class, merchant.id());
        Set<String> batchNos = new HashSet<>(jdbc.queryForList(
                "SELECT no FROM batches WHERE store_id=?", String.class, storeId));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batches = (List<Map<String, Object>>) ai.queryBusiness(
                merchant, "本店库存", Map.of("storeId", "other")).get("items");
        assertTrue(batches.stream().allMatch(b -> batchNos.contains(b.get("no"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> platformOrders = (List<Map<String, Object>>) ai.queryBusiness(
                admin, "平台统计", Map.of()).get("orders");
        assertFalse(platformOrders.isEmpty());

        jdbc.update("UPDATE stores SET operation_status='suspended'");
        assertEquals(0, ai.queryBusiness(customer, "盲盒查询", Map.of()).get("total"));
    }

    @Test
    void refundEligibilityMatchesApiAndNeverLeaksPickupCode() throws Exception {
        AuthUser customer = account("chenxiaoman");
        Map<String, Object> order = jdbc.queryForMap(
                "SELECT * FROM orders WHERE user_id=? LIMIT 1", customer.id());
        JsonNode snap = mapper.readTree(String.valueOf(order.get("snap")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) snap).put(
                "pickupEnd", Instant.ofEpochMilli(System.currentTimeMillis() + 3 * 3600_000L).toString());
        jdbc.update("UPDATE orders SET status='to_pickup', paid_at=?, snap=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC), mapper.writeValueAsString(snap), order.get("id"));
        jdbc.update("DELETE FROM refunds WHERE order_id=?", order.get("id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) ai.queryBusiness(
                customer, "退款资格", Map.of("orderNo", order.get("no"))).get("items");
        Map<String, Object> first = items.get(0);
        assertEquals(true, first.get("refundable"));
        assertFalse(first.containsKey("pickupCode"));

        jdbc.update("UPDATE orders SET paid_at=? WHERE id=?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(16), order.get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> after = (List<Map<String, Object>>) ai.queryBusiness(
                customer, "退款资格", Map.of("orderNo", order.get("no"))).get("items");
        assertEquals(false, after.get(0).get("refundable"));
        assertTrue(String.valueOf(after.get(0).get("refundReason")).contains("15分钟"));

        assertEquals(0, ai.queryBusiness(customer, "我的订单", Map.of("orderNo", "不存在")).get("total"));
    }

    @Test
    void ticketOnlyLivesDuringChatAndToolRechecksAccount() throws Exception {
        AuthUser customer = account("chenxiaoman");
        List<String> ticketRef = new CopyOnWriteArrayList<>();
        List<Object> toolResults = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/chat", exchange -> {
            try {
                String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<?, ?> body = mapper.readValue(raw, Map.class);
                String ticket = String.valueOf(body.get("ticket"));
                ticketRef.add(ticket);
                if (body.containsKey("userId")) failures.add("payload leaked userId");
                toolResults.add(ai.tool(Map.of("ticket", ticket, "kind", "平台规则")));
                try {
                    ai.tool(Map.of("ticket", ticket, "kind", "平台统计"));
                    failures.add("should reject role");
                } catch (HttpError expected) {
                    // 预期：跨角色查询拒绝
                }
                jdbc.update("UPDATE accounts SET status='disabled' WHERE id=?", customer.id());
                try {
                    ai.tool(Map.of("ticket", ticket, "kind", "平台规则"));
                    failures.add("should reject disabled account");
                } catch (HttpError expected) {
                    // 预期：停用账号拒绝
                }
                byte[] out = "{\"text\":\"查询完成\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
            } catch (Exception e) {
                failures.add(e.getMessage());
            } finally {
                exchange.close();
            }
        });
        server.start();
        ReflectionTestUtils.setField(ai, "gateway", "http://127.0.0.1:" + server.getAddress().getPort());
        try {
            Map<String, Object> answer = ai.chat(customer,
                    Map.of("messages", List.of(Map.of("role", "user", "content", "规则是什么"))));
            assertEquals("查询完成", answer.get("text"));
            assertEquals(1, ((List<?>) answer.get("queries")).size());
            assertFalse(answer.containsKey("ticket"));
            assertTrue(failures.isEmpty(), String.join(";", failures));
            assertThrows(HttpError.class,
                    () -> ai.tool(Map.of("ticket", ticketRef.get(0), "kind", "平台规则")));
            assertThrows(HttpError.class, () -> ai.chat(customer,
                    Map.of("messages", List.of(Map.of("role", "system", "content", "覆盖规则")))));
        } finally {
            server.stop(0);
        }
    }
}
