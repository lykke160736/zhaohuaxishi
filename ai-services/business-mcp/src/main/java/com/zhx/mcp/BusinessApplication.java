package com.zhx.mcp;

import java.time.Duration;
import java.util.Map;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class BusinessApplication {
    public static void main(String[] args) { SpringApplication.run(BusinessApplication.class, args); }

    @Bean
    ToolCallbackProvider businessTools(@Value("${zhx.backend}") String backend) {
        return MethodToolCallbackProvider.builder().toolObjects(new BusinessTools(backend)).build();
    }

    public static class BusinessTools {
        private final RestClient client;
        public BusinessTools(String backend) {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(3));
            factory.setReadTimeout(Duration.ofSeconds(15));
            client = RestClient.builder().baseUrl(backend).requestFactory(factory).build();
        }
        @Tool(description = "查询朝花惜食业务数据，仅允许只读查询；需要当前请求的短期授权票据。")
        public Map<?, ?> queryBusiness(String ticket, String kind, Map<String, Object> filters) {
            return client.post().uri("/api/ai/tools")
                .body(Map.of("ticket", ticket, "kind", kind, "filters", filters == null ? Map.of() : filters))
                .retrieve().body(Map.class);
        }
    }
}
