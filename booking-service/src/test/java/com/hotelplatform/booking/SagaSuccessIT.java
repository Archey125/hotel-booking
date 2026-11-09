package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.dto.RoomRecommendationDto;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SagaSuccessIT extends BookingIntegrationTest {

    @MockitoBean
    HotelClient hotelClient;

    @Test
    void createBookingWithAutoSelectConfirmsAndReturnsConfirmed() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);
        UUID requestId = UUID.randomUUID();

        // возвращает наименее загруженный номер 42
        when(hotelClient.recommend(start, end)).thenReturn(List.of(
                new RoomRecommendationDto(42L, 1L, "Grand Moscow", "103", true, 0, null)));
        when(hotelClient.confirmAvailability(eq(42L), any())).thenReturn(
                new ConfirmAvailabilityResponse(requestId, 42L, 7L, "HELD", start, end));

        String body = objectMapper.writeValueAsString(new BookingPayload(99L, true, start, end, requestId));

        // заголовок X-Request-Id другой — requestId из тела должен иметь приоритет
        String response = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", registerAndToken("saga-user-" + System.nanoTime()))
                        .header("X-Request-Id", UUID.randomUUID())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.roomId").value(42))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andReturn().getResponse().getContentAsString();

        // в Hotel ушёл confirm с правильными данными и bookingId
        ArgumentCaptor<com.hotelplatform.common.api.ConfirmAvailabilityRequest> captor =
                ArgumentCaptor.forClass(com.hotelplatform.common.api.ConfirmAvailabilityRequest.class);
        verify(hotelClient).confirmAvailability(eq(42L), captor.capture());
        var sent = captor.getValue();
        assertThat(sent.bookingId()).isEqualTo(objectMapper.readTree(response).get("id").asLong());
        assertThat(sent.requestId()).isEqualTo(requestId);
        assertThat(sent.startDate()).isEqualTo(start);
        assertThat(sent.endDate()).isEqualTo(end);
    }

    @Test
    void getBookingReturnsCreatedBookingForOwner() throws Exception {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(6);
        UUID requestId = UUID.randomUUID();
        String token = registerAndToken("saga-owner-" + System.nanoTime());

        when(hotelClient.confirmAvailability(eq(5L), any())).thenReturn(
                new ConfirmAvailabilityResponse(requestId, 5L, 1L, "HELD", start, end));
        String created = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new BookingPayload(5L, false, start, end, requestId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/bookings/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));

        // история пользователя содержит бронь
        mockMvc.perform(get("/api/bookings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    record BookingPayload(Long roomId, Boolean autoSelect, LocalDate startDate, LocalDate endDate, UUID requestId) {
    }
}
