package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.AftersaleService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AftersaleController {
    private final AftersaleService aftersales;
    private final ServiceSupport support;

    public AftersaleController(AftersaleService aftersales, ServiceSupport support) {
        this.aftersales = aftersales;
        this.support = support;
    }

    @PostMapping("/api/aftersales/{id}/escalate")
    public Map<String, Object> escalate(@PathVariable String id) {
        return aftersales.escalate(AuthContext.get(), id);
    }

    @PostMapping("/api/aftersales/{id}/handle")
    public Map<String, Object> handle(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return aftersales.handle(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/aftersales/{id}/resolve")
    public Map<String, Object> resolve(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return aftersales.resolve(AuthContext.get(), id, support.body(body));
    }
}
