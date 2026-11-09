package com.hotelplatform.hotel.controller;

import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import com.hotelplatform.common.api.ReleaseRequest;
import com.hotelplatform.common.api.ReleaseResponse;
import com.hotelplatform.hotel.service.RoomLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// внутренние шаги саги, наружу через gateway не отдаются - только для booking-service
@Tag(name = "Internal saga steps", description = "Внутренние шаги саги (сервисный токен INTERNAL)")
@RestController
@RequestMapping("/internal/rooms")
@RequiredArgsConstructor
public class InternalRoomController {

    private final RoomLockService roomLockService;

    @Operation(summary = "[INTERNAL] Подтвердить доступность номера: временная блокировка слота на даты")
    @PostMapping("/{id}/confirm-availability")
    public ConfirmAvailabilityResponse confirmAvailability(@PathVariable Long id,
                                                           @Valid @RequestBody ConfirmAvailabilityRequest request) {
        return roomLockService.confirm(id, request);
    }

    @Operation(summary = "[INTERNAL] Компенсация: снять временную блокировку слота (идемпотентно)")
    @PostMapping("/{id}/release")
    public ReleaseResponse release(@PathVariable Long id,
                                   @Valid @RequestBody ReleaseRequest request) {
        return roomLockService.release(id, request);
    }
}
