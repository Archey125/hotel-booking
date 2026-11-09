package com.hotelplatform.hotel.dto;

import jakarta.validation.constraints.Size;

public record HotelUpdateRequest(
        @Size(max = 120) String name,
        @Size(max = 255) String address,
        @Size(max = 80) String city
) {
}
