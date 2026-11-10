package com.zhx.domain;

import java.time.LocalDateTime;

public record AuthUser(
        String id,
        String name,
        String account,
        String mobile,
        String phone,
        String role,
        String status,
        LocalDateTime createdAt
) {
    public boolean isCustomer() {
        return "customer".equals(role);
    }

    public boolean isMerchant() {
        return "merchant".equals(role);
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
