package com.hotelplatform.hotel.dto;

import java.math.BigDecimal;

public record RoomDto(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked,
        BigDecimal pricePerNight
) {
}
