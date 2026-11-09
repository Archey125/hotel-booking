package com.hotelplatform.common.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseRequest(
        @NotNull UUID requestId,
        Long bookingId
) {
}
