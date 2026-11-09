package com.hotelplatform.hotel.controller;

import com.hotelplatform.common.web.PageResponse;
import com.hotelplatform.hotel.dto.HotelCreateRequest;
import com.hotelplatform.hotel.dto.HotelDto;
import com.hotelplatform.hotel.dto.HotelUpdateRequest;
import com.hotelplatform.hotel.service.HotelService;
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

@Tag(name = "Hotels", description = "CRUD отелей")
@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HotelController {

    private final HotelService hotelService;

    @Operation(summary = "Добавить отель (ADMIN)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HotelDto> create(@Valid @RequestBody HotelCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.create(request));
    }

    @Operation(summary = "Обновить отель (ADMIN)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelDto update(@PathVariable Long id, @Valid @RequestBody HotelUpdateRequest request) {
        return hotelService.update(id, request);
    }

    @Operation(summary = "Удалить отель вместе с номерами (ADMIN)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Список отелей (USER/ADMIN), фильтры name/city, пагинация")
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PageResponse<HotelDto> list(@RequestParam(required = false) String name,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.of(hotelService.list(name, city, pageable));
    }

    @Operation(summary = "Отель по id (USER/ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public HotelDto get(@PathVariable Long id) {
        return hotelService.get(id);
    }
}
