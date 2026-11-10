package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.AuthService;
import com.zhx.service.ServiceSupport;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final ServiceSupport support;

    public AuthController(AuthService auth, ServiceSupport support) {
        this.auth = auth;
        this.support = support;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) Map<String, Object> body) {
        return auth.login(support.body(body));
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody(required = false) Map<String, Object> body) {
        return auth.register(support.body(body));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        return auth.logout(AuthContext.get());
    }
}
