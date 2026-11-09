package com.hotelplatform.hotel.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomUpdateRequest(
        @Size(max = 20) String number,
        Boolean available,
        BigDecimal pricePerNight
) {
}
