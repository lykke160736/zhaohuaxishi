package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.AdminService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {
    private final AdminService admin;
    private final ServiceSupport support;

    public AdminController(AdminService admin, ServiceSupport support) {
        this.admin = admin;
        this.support = support;
    }

    @PostMapping("/api/applications/{id}/audit")
    public Map<String, Object> audit(@PathVariable String id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        admin.auditApplication(AuthContext.get().id(), id, support.body(body));
        return Map.of();
    }

    @PostMapping("/api/admin/stores/{id}/operation")
    public Map<String, Object> storeOperation(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        admin.setStoreOperation(AuthContext.get().id(), id, support.body(body));
        return Map.of();
    }

    @PostMapping("/api/admin/batches/{id}/stop")
    public Map<String, Object> stopBatch(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        String reason = body != null ? String.valueOf(body.getOrDefault("reason", "平台下架")) : "平台下架";
        admin.adminStopBatch(AuthContext.get().id(), id, reason);
        return Map.of();
    }

    @PostMapping("/api/accounts/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable String id) {
        admin.toggleAccount(AuthContext.get().id(), id);
        return Map.of();
    }

    @PostMapping("/api/demo/refresh-window")
    public Map<String, Object> refreshWindow() {
        int changed = admin.refreshDemoWindow(AuthContext.get().id());
        return Map.of("changed", changed);
    }
}
