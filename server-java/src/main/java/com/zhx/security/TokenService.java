package com.zhx.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhx.repository.SettingsRepository;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TokenService {
    private final SettingsRepository settings;
    private final ObjectMapper mapper = new ObjectMapper();

    public TokenService(SettingsRepository settings) {
        this.settings = settings;
    }

    public String secret() {
        return settings.findValue("token_secret");
    }

    public String sign(String accountId, long ttlSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", accountId);
        payload.put("exp", System.currentTimeMillis() + ttlSeconds * 1000);
        payload.put("n", UUID.randomUUID().toString());
        String body = base64Url(JsonBytes.write(payload, mapper));
        String signature = hmac(body, secret());
        return body + "." + signature;
    }

    public String read(String token) {
        if (token == null || token.isBlank() || !token.contains(".")) return null;
        int dot = token.indexOf('.');
        String payload = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        String want = hmac(payload, secret());
        byte[] a = sig.getBytes(StandardCharsets.UTF_8);
        byte[] b = want.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length || !MessageDigest.isEqual(a, b)) return null;
        try {
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            Map<?, ?> data = mapper.readValue(json, Map.class);
            Object uid = data.get("uid");
            Object exp = data.get("exp");
            if (uid == null || exp == null) return null;
            double expires = ((Number) exp).doubleValue();
            if (System.currentTimeMillis() > expires) return null;
            return String.valueOf(uid);
        } catch (Exception e) {
            return null;
        }
    }

    private String hmac(String payload, String secret) {
        if (secret == null) throw new IllegalStateException("token_secret is not initialized");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class JsonBytes {
        static byte[] write(Object value, ObjectMapper mapper) {
            try {
                return mapper.writeValueAsBytes(value);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
