package com.hotelplatform.common.web;

import java.time.Instant;

// единая модель ошибки, traceId - сквозной id запроса
public record ErrorDto(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId
) {

    public static ErrorDto of(int status, String error, String message, String path) {
        return new ErrorDto(Instant.now(), status, error, message, path, org.slf4j.MDC.get(TraceIdFilter.MDC_KEY));
    }
}
