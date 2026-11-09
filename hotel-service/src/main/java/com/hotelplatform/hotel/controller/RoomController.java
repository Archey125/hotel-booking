package com.hotelplatform.hotel.controller;

import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import com.hotelplatform.common.api.ReleaseRequest;
import com.hotelplatform.common.api.ReleaseResponse;
import com.hotelplatform.common.web.PageResponse;
import com.hotelplatform.hotel.dto.RoomCreateRequest;
import com.hotelplatform.hotel.dto.RoomDto;
import com.hotelplatform.hotel.dto.RoomUpdateRequest;
import com.hotelplatform.hotel.service.RoomLockService;
import com.hotelplatform.hotel.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Rooms", description = "Номера: CRUD, свободные номера, рекомендации, шаги саги")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

    private final RoomService roomService;
    private final RoomLockService roomLockService;

    @Operation(summary = "Добавить номер в отель (ADMIN)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDto> create(@Valid @RequestBody RoomCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request));
    }

    @Operation(summary = "Обновить номер (ADMIN)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return roomService.update(id, request);
    }

    @Operation(summary = "Удалить номер (ADMIN)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Номер по id (USER/ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public RoomDto get(@PathVariable Long id) {
        return roomService.get(id);
    }

    @Operation(summary = "Свободные номера (USER/ADMIN); фильтры hotelId, даты, цена; пагинация/сортировка")
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResponse<RoomDto> listFree(
            @RequestParam(required = false) Long hotelId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sort) {
        Sort sorting = sort.endsWith(",desc")
                ? Sort.by(Sort.Direction.DESC, sort.substring(0, sort.length() - 5))
                : Sort.by(Sort.Direction.ASC, sort);
        Pageable pageable = PageRequest.of(page, size, sorting);
        return PageResponse.of(roomService.listFree(hotelId, startDate, endDate, minPrice, maxPrice, pageable));
    }

    @Operation(summary = "Рекомендованные номера: свободные, отсортированные по times_booked ASC, затем id ASC (USER/ADMIN; INTERNAL — автоподбор в саге)")
    @GetMapping("/recommend")
    @PreAuthorize("hasAnyRole('USER','ADMIN','INTERNAL')")
    public List<RoomDto> recommend(@RequestParam(required = false) Long hotelId,
                                   @RequestParam(required = false) LocalDate startDate,
                                   @RequestParam(required = false) LocalDate endDate) {
        return roomService.recommend(hotelId, startDate, endDate);
    }

    // шаги саги: только INTERNAL (сервисный токен booking-service)

    @Operation(summary = "[INTERNAL] Подтвердить доступность номера: временная блокировка слота на даты")
    @PostMapping("/{id}/confirm-availability")
    @PreAuthorize("hasRole('INTERNAL')")
    public ConfirmAvailabilityResponse confirmAvailability(@PathVariable Long id,
                                                           @Valid @RequestBody ConfirmAvailabilityRequest request) {
        return roomLockService.confirm(id, request);
    }

    @Operation(summary = "[INTERNAL] Компенсация: снять временную блокировку слота (идемпотентно)")
    @PostMapping("/{id}/release")
    @PreAuthorize("hasRole('INTERNAL')")
    public ReleaseResponse release(@PathVariable Long id,
                                   @Valid @RequestBody ReleaseRequest request) {
        return roomLockService.release(id, request);
    }
}
