package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.AftersaleService;
import com.zhx.service.OrderService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OrderController {
    private final OrderService orders;
    private final AftersaleService aftersales;
    private final ServiceSupport support;

    public OrderController(OrderService orders, AftersaleService aftersales, ServiceSupport support) {
        this.orders = orders;
        this.aftersales = aftersales;
        this.support = support;
    }

    @PostMapping("/api/orders")
    public Map<String, Object> place(@RequestBody(required = false) Map<String, Object> body) {
        return orders.place(AuthContext.get(), support.body(body));
    }

    @PostMapping("/api/orders/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return orders.cancel(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/orders/{id}/pay")
    public Map<String, Object> pay(@PathVariable String id,
                                   @RequestBody(required = false) Map<String, Object> body) {
        return orders.pay(AuthContext.get(), id, support.body(body));
    }

    @GetMapping("/api/payments/{id}")
    public Map<String, Object> paymentStatus(@PathVariable String id) {
        return orders.paymentStatus(AuthContext.get(), id);
    }

    @PostMapping("/api/orders/{id}/refund")
    public Map<String, Object> refund(@PathVariable String id) {
        return orders.applyRefund(AuthContext.get(), id);
    }

    @PostMapping("/api/orders/{id}/aftersale")
    public Map<String, Object> aftersale(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        return aftersales.submit(AuthContext.get(), id, support.body(body));
    }

    @PostMapping("/api/orders/{id}/review")
    public Map<String, Object> review(@PathVariable String id,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return orders.review(AuthContext.get(), id, support.body(body));
    }
}
