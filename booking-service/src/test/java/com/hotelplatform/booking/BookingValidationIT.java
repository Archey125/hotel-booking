package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingValidationIT extends BookingIntegrationTest {

    @MockitoBean
    HotelClient hotelClient;

    @Test
    void endBeforeStartIsRejected() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", registerAndToken("val-1-" + System.nanoTime()))
                        .contentType("application/json")
                        .content(body(LocalDate.now().plusDays(5), LocalDate.now().plusDays(3), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void startDateInPastIsRejected() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", registerAndToken("val-2-" + System.nanoTime()))
                        .contentType("application/json")
                        .content(body(LocalDate.now().minusDays(2), LocalDate.now().plusDays(1), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualSelectWithoutRoomIdIsRejected() throws Exception {
        String json = "{\"autoSelect\": false, \"startDate\": \"" + LocalDate.now().plusDays(1)
                + "\", \"endDate\": \"" + LocalDate.now().plusDays(2) + "\"}";
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", registerAndToken("val-3-" + System.nanoTime()))
                        .contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingDatesAreRejected() throws Exception {
        String json = """
                {"roomId": 1, "autoSelect": false}
                """;
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", registerAndToken("val-4-" + System.nanoTime()))
                        .contentType("application/json").content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotCreateBooking() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content(body(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null)))
                .andExpect(status().isUnauthorized());
    }

    private String body(LocalDate start, LocalDate end, UUID requestId) {
        StringBuilder sb = new StringBuilder();
        if (start != null && end != null) {
            sb.append("{\"roomId\": 7, \"autoSelect\": false, \"startDate\": \"").append(start)
                    .append("\", \"endDate\": \"").append(end).append("\"");
        } else if (start != null) {
            sb.append("{\"autoSelect\": false, \"startDate\": \"").append(start)
                    .append("\", \"endDate\": \"").append(end).append("\"");
        } else {
            sb.append("{\"autoSelect\": false");
        }
        if (requestId != null) {
            sb.append(", \"requestId\": \"").append(requestId).append("\"");
        }
        return sb.append("}").toString();
    }
}
