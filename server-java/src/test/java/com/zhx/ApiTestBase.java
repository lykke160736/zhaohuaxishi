package com.zhx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhx.config.DatabaseBootstrap;
import com.zhx.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
/**
 * Java 侧对拍基类：每个用例前重建演示数据，关闭自动后台任务，
 * 由用例显式调用 settle/scanTimeouts 模拟 Node 的 ctx.runJobs()。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:zhx-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "NON_KEYWORDS=KEY,VALUE,READ,TIME,NO;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.auto-settle=false",
        "app.demo.seed=true",
        "logging.level.root=WARN",
        "logging.level.com.zhx=WARN"
})
public abstract class ApiTestBase {

    public record Resp(int status, JsonNode data) {
    }

    public record Purchase(String batchId, String orderId, String paymentId) {
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper om;

    @Autowired
    protected DatabaseBootstrap bootstrap;

    @Autowired
    protected PaymentService payments;

    @BeforeEach
    void resetDemo() throws Exception {
        bootstrap.resetDemoData();
    }

    protected Resp call(String method, String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder request = "GET".equals(method)
                ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                : org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(body));
        }
        var result = mvc.perform(request).andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        JsonNode data = bytes.length == 0 ? om.createObjectNode() : om.readTree(bytes);
        return new Resp(result.getResponse().getStatus(), data);
    }

    protected Resp get(String path, String token) throws Exception {
        return call("GET", path, token, null);
    }

    protected Resp post(String path, String token, Object body) throws Exception {
        return call("POST", path, token, body);
    }

    protected String login(String account, String role) throws Exception {
        Resp resp = post("/api/auth/login", null,
                java.util.Map.of("account", account, "password", "123456", "role", role));
        assertEquals(200, resp.status(), String.valueOf(resp.data()));
        String token = resp.data().path("token").asText();
        assertNotNull(token);
        return token;
    }

    protected JsonNode state(String token) throws Exception {
        Resp resp = get("/api/bootstrap", token);
        assertEquals(200, resp.status(), String.valueOf(resp.data()));
        return resp.data().path("state");
    }

    protected JsonNode pickBatch(JsonNode state, long minLeftMs) {
        for (JsonNode batch : state.path("batches")) {
            long left = java.time.Instant.parse(batch.path("pickupEnd").asText()).toEpochMilli()
                    - System.currentTimeMillis();
            int available = batch.path("total").asInt() - batch.path("sold").asInt()
                    - batch.path("reserved").asInt();
            if ("on_sale".equals(batch.path("status").asText()) && available > 0 && left > minLeftMs) {
                return batch;
            }
        }
        throw new AssertionError("演示数据中应存在可售批次");
    }

    protected Purchase buyAndPay(String token, long minLeftMs) throws Exception {
        JsonNode batch = pickBatch(state(token), minLeftMs);
        Resp created = post("/api/orders", token, java.util.Map.of("batchId", batch.path("id").asText()));
        assertEquals(200, created.status(), String.valueOf(created.data()));
        String orderId = created.data().path("order").path("id").asText();
        Resp paid = post("/api/orders/" + orderId + "/pay", token, java.util.Map.of("channel", "wechat"));
        assertEquals(200, paid.status(), String.valueOf(paid.data()));
        return new Purchase(batch.path("id").asText(), orderId,
                paid.data().path("payment").path("id").asText());
    }

    /** 模拟 Node 测试的 ctx.runJobs()：支付回调 + 退款到账 + 超时扫描。 */
    protected void runJobs() {
        payments.settleDue(0, 0);
        payments.scanTimeouts();
    }
}
