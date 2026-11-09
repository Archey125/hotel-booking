package com.hotelplatform.booking.dto;

import com.hotelplatform.booking.entity.BookingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingDto(
        Long id,
        Long userId,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        UUID requestId,
        Instant createdAt,
        Instant updatedAt
) {
}
