package com.hotelplatform.common.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// один и тот же base64-секрет: booking выпускает токены, остальные сервисы проверяют
public final class JwtDecoderFactory {

    private JwtDecoderFactory() {
    }

    public static JwtDecoder hmac(String base64Secret) {
        byte[] raw = Base64.getDecoder().decode(base64Secret);
        if (raw.length < 32) {
            throw new IllegalStateException("JWT secret слишком короткий: нужно минимум 32 байта для HS256");
        }
        SecretKey key = new SecretKeySpec(raw, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    public static byte[] decodeSecret(String base64Secret) {
        return Base64.getDecoder().decode(base64Secret.getBytes(StandardCharsets.UTF_8));
    }
}
