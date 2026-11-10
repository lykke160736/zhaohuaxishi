package com.zhx.config;

import com.zhx.domain.AuthUser;
import com.zhx.security.AuthContext;
import com.zhx.service.StateService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ResponseAdvice implements ResponseBodyAdvice<Object> {
    private final StateService stateService;

    public ResponseAdvice(StateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Map<?, ?> map) || !request.getURI().getPath().startsWith("/api/")) {
            return body;
        }
        if (response instanceof ServletServerHttpResponse servlet && servlet.getServletResponse().getStatus() >= 400) {
            return body;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        AuthUser me = AuthContext.get();
        if (me != null && !out.containsKey("state")) {
            out.put("state", stateService.build(me));
        }
        return out;
    }
}
