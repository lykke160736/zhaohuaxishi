package com.zhx.security;

import org.bouncycastle.crypto.generators.SCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class PasswordService {
    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);
    private static final int N = 16384;
    private static final int R = 8;
    private static final int P = 1;
    private static final int KEY_LEN = 64;
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String saltHex = hex(salt);
        byte[] hash = SCrypt.generate(password.getBytes(StandardCharsets.UTF_8),
                saltHex.getBytes(StandardCharsets.UTF_8), N, R, P, KEY_LEN);
        return "scrypt$" + N + "$" + R + "$" + P + "$" + saltHex + "$" + hex(hash);
    }

    public boolean verify(String password, String stored) {
        try {
            String[] parts = String.valueOf(stored == null ? "" : stored).split("\\$");
            if (parts.length != 6 || !"scrypt".equals(parts[0]) || parts[4].isEmpty() || parts[5].isEmpty()) {
                return false;
            }
            int n = Integer.parseInt(parts[1]);
            int r = Integer.parseInt(parts[2]);
            int p = Integer.parseInt(parts[3]);
            int keyLen = parts[5].length() / 2;
            byte[] calc = SCrypt.generate(String.valueOf(password).getBytes(StandardCharsets.UTF_8),
                    parts[4].getBytes(StandardCharsets.UTF_8), n, r, p, keyLen);
            byte[] known = unhex(parts[5]);
            return MessageDigest.isEqual(calc, known);
        } catch (Exception e) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] unhex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
