package com.hotelplatform.common.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// выпуск jwt (HS256): пользовательские токены (USER/ADMIN) и сервисные (INTERNAL)
// сделано напрямую через nimbus-jose-jwt, чтобы класс работал и без spring-контекста
public final class JwtTokens {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLE = "role";

    private JwtTokens() {
    }

    public static String issue(String base64Secret, String issuer, String subject, Long userId,
                               String role, Duration ttl) {
        try {
            SecretKey key = new SecretKeySpec(JwtDecoderFactory.decodeSecret(base64Secret), "HmacSHA256");
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .issuer(issuer)
                    .subject(subject)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttl)))
                    .claim(CLAIM_ROLE, role);
            if (userId != null) {
                claims.claim(CLAIM_UID, userId);
            }
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT)
                    .keyID("hotel-platform")
                    .build();
            SignedJWT signed = new SignedJWT(header, claims.build());
            signed.sign(new MACSigner(key));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось выпустить JWT", e);
        }
    }

    // сервисный токен для вызовов booking -> hotel
    public static String issueInternal(String base64Secret, String issuer) {
        return issue(base64Secret, issuer, "booking-service", null, "INTERNAL", Duration.ofMinutes(10));
    }

    // обёртка, когда нужен именно spring-ов JwtEncoder
    public static JwtEncoder encoder(String base64Secret) {
        SecretKey key = new SecretKeySpec(JwtDecoderFactory.decodeSecret(base64Secret), "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key).keyID("hotel-platform").build();
        JWKSet jwkSet = new JWKSet(jwk);
        return new NimbusJwtEncoder((jwkSelector, securityContext) -> jwkSelector.select(jwkSet));
    }
}
