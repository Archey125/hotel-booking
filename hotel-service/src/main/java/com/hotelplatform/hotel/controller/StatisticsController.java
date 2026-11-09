package com.hotelplatform.hotel.controller;

import com.hotelplatform.hotel.dto.OccupancyStatDto;
import com.hotelplatform.hotel.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Statistics", description = "Статистика загруженности номеров (только ADMIN)")
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "Загруженность номеров: times_booked, активные блокировки, заблокированные сутки в периоде (ADMIN)")
    @GetMapping("/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OccupancyStatDto> occupancy(@RequestParam(required = false) Long hotelId,
                                            @RequestParam(required = false) LocalDate dateFrom,
                                            @RequestParam(required = false) LocalDate dateTo) {
        return statisticsService.occupancy(hotelId, dateFrom, dateTo);
    }
}
