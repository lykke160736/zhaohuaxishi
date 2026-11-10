package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.CustomerService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CustomerController {
    private final CustomerService customer;
    private final ServiceSupport support;

    public CustomerController(CustomerService customer, ServiceSupport support) {
        this.customer = customer;
        this.support = support;
    }

    @PostMapping("/api/location")
    public Map<String, Object> location(@RequestBody(required = false) Map<String, Object> body) {
        customer.setBrowseCenter(AuthContext.get().id(), support.body(body));
        return Map.of();
    }

    @PostMapping("/api/favorites/{storeId}")
    public Map<String, Object> favorite(@PathVariable String storeId) {
        customer.toggleFavorite(AuthContext.get().id(), storeId);
        return Map.of();
    }

    @PostMapping("/api/notices/{id}/read")
    public Map<String, Object> noticeRead(@PathVariable String id) {
        customer.markNoticeRead(AuthContext.get().id(), id);
        return Map.of();
    }

    @PostMapping("/api/notices/read-all")
    public Map<String, Object> noticeReadAll() {
        customer.markNoticeReadAll(AuthContext.get().id());
        return Map.of();
    }

    @PostMapping("/api/wallet/redeem")
    public Map<String, Object> redeem() {
        return customer.redeemCoupon(AuthContext.get().id());
    }
}
