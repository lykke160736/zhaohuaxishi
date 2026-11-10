package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.AiService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AiController {
    private final AiService ai;
    private final ServiceSupport support;

    public AiController(AiService ai, ServiceSupport support) {
        this.ai = ai;
        this.support = support;
    }

    @PostMapping("/api/ai/chat")
    public Map<String, Object> chat(@RequestBody(required = false) Map<String, Object> body) {
        return ai.chat(AuthContext.get(), support.body(body));
    }

    @GetMapping("/api/ai/status")
    public Map<String, Object> status() {
        return ai.status();
    }

    @PostMapping("/api/ai/tools")
    public Map<String, Object> tools(@RequestBody(required = false) Map<String, Object> body) {
        return ai.tool(support.body(body));
    }
}
