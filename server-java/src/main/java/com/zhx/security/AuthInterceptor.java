package com.zhx.security;

import com.zhx.domain.AuthUser;
import com.zhx.exception.HttpError;
import com.zhx.repository.DbRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login", "/api/auth/register", "/api/bootstrap", "/api/ai/status", "/api/ai/tools");

    private final TokenService tokens;
    private final DbRepository db;

    public AuthInterceptor(TokenService tokens, DbRepository db) {
        this.tokens = tokens;
        this.db = db;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        boolean hasToken = token != null && !token.isBlank();
        AuthUser me = null;
        if (hasToken) {
            String uid = tokens.read(token);
            if (uid != null) {
                AuthUser account = db.accountById(uid);
                if (account != null && "active".equals(account.status())) {
                    me = account;
                }
            }
        }
        if (hasToken && me == null && !path.startsWith("/api/auth/")) {
            throw HttpError.unauthorized("登录状态已失效，请重新登录");
        }
        if (!PUBLIC_PATHS.contains(path) && me == null) {
            throw HttpError.unauthorized("请先登录");
        }
        AuthContext.set(me);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
