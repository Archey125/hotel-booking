package com.hotelplatform.booking;

import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaginationIT extends BookingIntegrationTest {

    @Test
    void historyIsPaginatedAndFiltered() throws Exception {
        String username = "pager-" + System.nanoTime();
        String token = registerAndToken(username);
        Long userId = userIdByUsername(username);

        for (int i = 0; i < 3; i++) {
            bookingRepository.save(Booking.builder()
                    .userId(userId)
                    .roomId((long) (100 + i))
                    .startDate(LocalDate.now().plusDays(i + 1))
                    .endDate(LocalDate.now().plusDays(i + 2))
                    .status(i == 0 ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED)
                    .requestId(UUID.randomUUID())
                    .build());
        }

        // страница размером 2
        mockMvc.perform(get("/api/bookings?page=0&size=2").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        // фильтр по статусу
        mockMvc.perform(get("/api/bookings?status=CONFIRMED").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // фильтр по датам: только брони, пересекающиеся с периодом
        mockMvc.perform(get("/api/bookings?dateFrom=" + LocalDate.now().plusDays(1)
                        + "&dateTo=" + LocalDate.now().plusDays(10))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
