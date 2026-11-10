package com.zhx.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class BodyLimitFilter extends OncePerRequestFilter {
    private static final long MAX_BODY = 1024L * 1024L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            long length = request.getContentLengthLong();
            if (length > MAX_BODY) {
                response.setStatus(413);
                response.setContentType("application/json; charset=utf-8");
                response.getWriter().write("{\"ok\":false,\"msg\":\"请求体过大\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
