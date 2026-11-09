package com.hotelplatform.hotel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.saga")
public record SagaProperties(
        long lockTtlMinutes
) {
    public SagaProperties {
        if (lockTtlMinutes <= 0) {
            lockTtlMinutes = 15;
        }
    }
}
