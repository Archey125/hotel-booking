package com.hotelplatform.booking.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMinutes,
        UserDto user
) {
}
