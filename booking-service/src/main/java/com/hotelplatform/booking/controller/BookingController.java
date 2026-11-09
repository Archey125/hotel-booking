package com.hotelplatform.booking.controller;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.dto.BookingDto;
import com.hotelplatform.booking.dto.CreateBookingRequest;
import com.hotelplatform.booking.dto.RoomRecommendationDto;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.service.BookingQueryService;
import com.hotelplatform.booking.service.BookingSagaService;
import com.hotelplatform.common.web.PageResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Bookings", description = "Бронирования: создание (с сагой), история, отмена")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingSagaService sagaService;
    private final BookingQueryService queryService;
    private final HotelClient hotelClient;

    @Operation(summary = "Создать бронирование (USER/ADMIN). autoSelect=true — автоподбор по times_booked; requestId/X-Request-Id — идемпотентность")
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<BookingDto> create(@Valid @RequestBody CreateBookingRequest request,
                                             @RequestHeader(name = "X-Request-Id", required = false) UUID headerRequestId,
                                             @AuthenticationPrincipal Jwt jwt) {
        var result = sagaService.create(currentUserId(jwt), request, headerRequestId);
        return ResponseEntity.status(result.newlyCreated() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.booking());
    }

    @Operation(summary = "История своих бронирований (USER/ADMIN): фильтры status/dateFrom/dateTo, пагинация")
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResponse<BookingDto> myBookings(@RequestParam(required = false) BookingStatus status,
                                               @RequestParam(required = false) LocalDate dateFrom,
                                               @RequestParam(required = false) LocalDate dateTo,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @AuthenticationPrincipal Jwt jwt) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return BookingQueryService.toPage(
                queryService.listOwn(currentUserId(jwt), status, dateFrom, dateTo, pageable)
                        .map(this::toDto));
    }

    @Operation(summary = "Все бронирования (ADMIN)")
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<BookingDto> allBookings(@RequestParam(required = false) Long userId,
                                                @RequestParam(required = false) BookingStatus status,
                                                @RequestParam(required = false) LocalDate dateFrom,
                                                @RequestParam(required = false) LocalDate dateTo,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return BookingQueryService.toPage(
                queryService.listAll(status, dateFrom, dateTo, pageable).map(this::toDto));
    }

    @Operation(summary = "Бронирование по id (владелец или ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingDto get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Booking booking = queryService.getAccessible(currentUserId(jwt), id, isAdmin(jwt));
        return toDto(booking);
    }

    @Operation(summary = "Отменить бронирование (владелец или ADMIN): CANCELLED + компенсация release в Hotel Service")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public BookingDto cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return sagaService.cancel(currentUserId(jwt), id, isAdmin(jwt));
    }

    @Operation(summary = "Рекомендованные номера для дат (прокси к Hotel Service: сортировка по times_booked ASC)")
    @GetMapping("/recommendations")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<RoomRecommendationDto> recommendations(@RequestParam LocalDate startDate,
                                                       @RequestParam LocalDate endDate) {
        return hotelClient.recommend(startDate, endDate);
    }

    // uid и роль берём прямо из клеймов токена
    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getClaimAsString("uid"));
    }

    private boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    private BookingDto toDto(Booking booking) {
        return new BookingDto(booking.getId(), booking.getUserId(), booking.getRoomId(),
                booking.getStartDate(), booking.getEndDate(), booking.getStatus(),
                booking.getRequestId(), booking.getCreatedAt(), booking.getUpdatedAt());
    }
}
