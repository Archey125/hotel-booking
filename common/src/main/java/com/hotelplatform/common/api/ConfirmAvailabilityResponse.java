package com.hotelplatform.common.api;

import java.time.LocalDate;
import java.util.UUID;

public record ConfirmAvailabilityResponse(
        UUID requestId,
        Long roomId,
        Long lockId,
        String status,
        LocalDate startDate,
        LocalDate endDate
) {
}
