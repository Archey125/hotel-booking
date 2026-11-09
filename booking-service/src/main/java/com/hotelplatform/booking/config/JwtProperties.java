package com.hotelplatform.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long ttlMinutes
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "booking-service";
        }
        if (ttlMinutes <= 0) {
            ttlMinutes = 60;
        }
    }
}
