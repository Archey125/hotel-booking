package com.hotelplatform.booking.dto;

import java.math.BigDecimal;

public record RoomRecommendationDto(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked,
        BigDecimal pricePerNight
) {
}
