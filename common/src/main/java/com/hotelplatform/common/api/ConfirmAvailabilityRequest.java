package com.hotelplatform.common.api;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ConfirmAvailabilityRequest(
        @NotNull UUID requestId,
        @NotNull Long bookingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
