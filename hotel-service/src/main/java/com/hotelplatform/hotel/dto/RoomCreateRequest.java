package com.hotelplatform.hotel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RoomCreateRequest(
        @NotNull Long hotelId,
        @NotBlank @Size(max = 20) String number,
        Boolean available,
        @Positive BigDecimal pricePerNight
) {
}
