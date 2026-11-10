package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.MerchantService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MerchantController {
    private final MerchantService merchant;
    private final ServiceSupport support;

    public MerchantController(MerchantService merchant, ServiceSupport support) {
        this.merchant = merchant;
        this.support = support;
    }

    @PostMapping("/api/drafts")
    public Map<String, Object> saveDraft(@RequestBody(required = false) Map<String, Object> body) {
        return merchant.saveDraft(AuthContext.get(), support.body(body));
    }

    @PostMapping("/api/drafts/{id}/publish")
    public Map<String, Object> publishDraft(@PathVariable String id) {
        return merchant.publishDraft(AuthContext.get(), id);
    }

    @PostMapping("/api/batches")
    public Map<String, Object> publishBatch(@RequestBody(required = false) Map<String, Object> body) {
        return merchant.publishBatch(AuthContext.get(), support.body(body));
    }

    @PostMapping("/api/batches/{id}/stock")
    public Map<String, Object> addStock(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        return merchant.addStock(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/batches/{id}/stop")
    public Map<String, Object> stopBatch(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        return merchant.stopBatch(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/verify")
    public Map<String, Object> verify(@RequestBody(required = false) Map<String, Object> body) {
        return merchant.verify(AuthContext.get(), support.body(body));
    }

    @PostMapping("/api/merchant/orders/{id}/cancel")
    public Map<String, Object> merchantCancel(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return merchant.merchantCancel(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/merchant/profile")
    public Map<String, Object> updateProfile(@RequestBody(required = false) Map<String, Object> body) {
        return merchant.updateProfile(AuthContext.get(), support.body(body));
    }
}
