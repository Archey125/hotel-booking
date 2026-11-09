package com.hotelplatform.common.api;

import java.util.UUID;

public record ReleaseResponse(
        UUID requestId,
        String status
) {
}
