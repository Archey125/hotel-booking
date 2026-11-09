package com.hotelplatform.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.hotel-client")
public record HotelClientProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs,
        int maxAttempts,
        long backoffInitialMs,
        double backoffMultiplier,
        long backoffMaxMs
) {
    public HotelClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://hotel-service";
        }
        if (maxAttempts < 1) {
            maxAttempts = 3;
        }
        if (backoffMultiplier < 1) {
            backoffMultiplier = 2.0;
        }
    }
}
