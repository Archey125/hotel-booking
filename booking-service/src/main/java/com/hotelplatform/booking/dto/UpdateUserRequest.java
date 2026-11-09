package com.hotelplatform.booking.dto;

import com.hotelplatform.booking.entity.Role;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 3, max = 64) String username,
        @Size(min = 6, max = 64) String password,
        Role role
) {
}
