package com.hotelplatform.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.saga")
public record BookingSagaProperties(
        long pendingTimeoutSeconds
) {
    public BookingSagaProperties {
        if (pendingTimeoutSeconds <= 0) {
            pendingTimeoutSeconds = 120;
        }
    }
}
