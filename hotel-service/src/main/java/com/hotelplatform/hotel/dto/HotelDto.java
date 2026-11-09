package com.hotelplatform.hotel.dto;

import java.time.Instant;

public record HotelDto(
        Long id,
        String name,
        String address,
        String city,
        long roomsCount,
        Instant createdAt
) {
}
