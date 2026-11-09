package com.hotelplatform.booking.dto;

import com.hotelplatform.booking.entity.Role;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        Role role,
        Instant createdAt
) {
}
