package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingAccessIT extends BookingIntegrationTest {

    @MockitoBean
    HotelClient hotelClient;

    @Test
    void userCanAccessOnlyOwnBookings() throws Exception {
        String ownerToken = registerAndToken("owner-" + System.nanoTime());
        String strangerToken = registerAndToken("stranger-" + System.nanoTime());
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);

        when(hotelClient.confirmAvailability(any(), any()))
                .thenAnswer(inv -> new ConfirmAvailabilityResponse(
                        UUID.randomUUID(), 30L, 1L, "HELD", start, end));

        String created = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", ownerToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new Payload(30L, false, start, end, UUID.randomUUID()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long bookingId = objectMapper.readTree(created).get("id").asLong();

        // владелец видит
        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", ownerToken))
                .andExpect(status().isOk());

        // чужой USER: 403 на чтение и отмену
        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", strangerToken))
                .andExpect(status().isForbidden());

        // админ видит любую бронь и может отменить
        mockMvc.perform(get("/api/bookings/" + bookingId).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/bookings/" + bookingId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void allBookingsEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/bookings/all").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());

        mockMvc.perform(get("/api/bookings/all").header("Authorization", registerAndToken("plain-" + System.nanoTime())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/all"))
                .andExpect(status().isUnauthorized());
    }

    record Payload(Long roomId, Boolean autoSelect, LocalDate startDate, LocalDate endDate, UUID requestId) {
    }
}
