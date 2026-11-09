package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.IntegrationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SagaFailureIT extends BookingIntegrationTest {

    @MockitoBean
    HotelClient hotelClient;

    @Test
    void hotelConflictCancelsBookingAndReturns409() throws Exception {
        String token = registerAndToken("saga-conflict-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        UUID requestId = UUID.randomUUID();

        when(hotelClient.confirmAvailability(eq(10L), any()))
                .thenThrow(new ConflictException("Номер id=10 уже занят на даты"));

        String body = objectMapper.writeValueAsString(new Payload(10L, false, start, end, requestId));
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Номер id=10 уже занят на даты"));

        // бронь компенсирована, компенсация release вызвана
        Booking saved = bookingRepository.findByRequestId(requestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(hotelClient).release(eq(10L), any());
    }

    @Test
    void hotelUnavailableAfterRetriesCancelsBookingAndReturns502() throws Exception {
        String token = registerAndToken("saga-timeout-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        UUID requestId = UUID.randomUUID();

        // исчерпаны повторы, тайм-ауты/сеть
        when(hotelClient.confirmAvailability(eq(11L), any()))
                .thenThrow(new IntegrationException("Hotel Service не ответил (тайм-аут/сеть)", null, true));

        String body = objectMapper.writeValueAsString(new Payload(11L, false, start, end, requestId));
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadGateway());

        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        verify(hotelClient).release(eq(11L), any());
    }

    @Test
    void noRoomsForDatesFailsBeforeBookingCreated() throws Exception {
        String token = registerAndToken("saga-empty-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        UUID requestId = UUID.randomUUID();

        when(hotelClient.recommend(start, end)).thenReturn(List.of());

        long before = bookingRepository.count();
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Payload(null, true, start, end, requestId))))
                .andExpect(status().isConflict());

        assertThat(bookingRepository.count()).isEqualTo(before);
        verify(hotelClient, never()).confirmAvailability(any(), any());
    }

    record Payload(Long roomId, Boolean autoSelect, LocalDate startDate, LocalDate endDate, UUID requestId) {
    }
}
