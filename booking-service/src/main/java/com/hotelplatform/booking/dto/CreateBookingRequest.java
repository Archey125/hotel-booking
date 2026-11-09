package com.hotelplatform.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        Long roomId,
        @NotNull Boolean autoSelect,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        UUID requestId
) {
}
