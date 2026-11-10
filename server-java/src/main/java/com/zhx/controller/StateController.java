package com.zhx.controller;

import com.zhx.security.AuthContext;
import com.zhx.service.StateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class StateController {
    private final StateService stateService;

    public StateController(StateService stateService) {
        this.stateService = stateService;
    }

    @GetMapping("/api/bootstrap")
    public Map<String, Object> bootstrap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", stateService.build(AuthContext.get()));
        return out;
    }
}
