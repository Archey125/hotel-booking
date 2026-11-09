package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyIT extends BookingIntegrationTest {

    @MockitoBean
    HotelClient hotelClient;

    @Test
    void repeatedRequestWithSameRequestIdReturnsSameBookingWithoutDuplicate() throws Exception {
        String token = registerAndToken("idem-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        UUID requestId = UUID.randomUUID();

        when(hotelClient.confirmAvailability(eq(20L), any()))
                .thenReturn(new ConfirmAvailabilityResponse(requestId, 20L, 3L, "HELD", start, end));

        String body = objectMapper.writeValueAsString(new Payload(20L, false, start, end, requestId));

        // первый запрос: 201, бронь CONFIRMED
        String first = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn().getResponse().getContentAsString();
        long firstId = objectMapper.readTree(first).get("id").asLong();

        // повтор (сеть/повторная доставка): 200, та же бронь, без нового confirm к Hotel
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(bookingRepository.findByRequestId(requestId).orElseThrow().getId()).isEqualTo(firstId);
        verify(hotelClient, times(1)).confirmAvailability(eq(20L), any());
    }

    @Test
    void sameRequestIdFromAnotherUserOrWithOtherPayloadIsRejected() throws Exception {
        String user1 = registerAndToken("idem-a-" + System.nanoTime());
        String user2 = registerAndToken("idem-b-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        UUID requestId = UUID.randomUUID();

        when(hotelClient.confirmAvailability(eq(21L), any()))
                .thenReturn(new ConfirmAvailabilityResponse(requestId, 21L, 3L, "HELD", start, end));

        String body = objectMapper.writeValueAsString(new Payload(21L, false, start, end, requestId));
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", user1).contentType("application/json").content(body))
                .andExpect(status().isCreated());

        // чужой requestId -> 409
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", user2).contentType("application/json").content(body))
                .andExpect(status().isConflict());

        // тот же requestId, другие даты -> 409, дубля нет
        String otherDates = objectMapper.writeValueAsString(new Payload(21L, false,
                start.plusDays(10), end.plusDays(10), requestId));
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", user1).contentType("application/json").content(otherDates))
                .andExpect(status().isConflict());

        assertThat(bookingRepository.count()).isEqualTo(1);
        Booking booking = bookingRepository.findByRequestId(requestId).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    record Payload(Long roomId, Boolean autoSelect, LocalDate startDate, LocalDate endDate, UUID requestId) {
    }
}
