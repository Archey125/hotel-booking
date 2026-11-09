package com.hotelplatform.booking.client;

import com.hotelplatform.booking.config.HotelClientProperties;
import com.hotelplatform.booking.config.JwtProperties;
import com.hotelplatform.booking.security.JwtService;
import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.IntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.CONFLICT;

class HotelClientRetryTest {

    private static final String SECRET = "aG90ZWwtcGxhdGZvcm0tand0LXNlY3JldC1rZXktMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final String HOTEL_URL = "http://hotel-service/internal/rooms/";

    private MockRestServiceServer server;
    private HotelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        builder.baseUrl("http://hotel-service");
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, "booking-service", 60));
        HotelClientProperties props = new HotelClientProperties(
                "http://hotel-service", 500, 500, 3, 10, 1.0, 20);
        client = new HotelClient(builder.build(), jwtService, props,
                (org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory) null,
                null);
    }

    @Test
    void retriesServerErrorsAndSucceedsOnThirdAttempt() {
        UUID requestId = UUID.randomUUID();
        String okJson = """
                {"requestId":"%s","roomId":5,"lockId":1,"status":"HELD",
                 "startDate":"2026-01-10","endDate":"2026-01-12"}
                """.formatted(requestId);
        // первая и вторая попытки — 500, третья — успех
        server.expect(requestTo(HOTEL_URL + "5/confirm-availability"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        server.expect(requestTo(HOTEL_URL + "5/confirm-availability"))
                .andRespond(withServerError());
        server.expect(requestTo(HOTEL_URL + "5/confirm-availability"))
                .andRespond(withSuccess(okJson, MediaType.APPLICATION_JSON));

        ConfirmAvailabilityResponse response = client.confirmAvailability(5L,
                new ConfirmAvailabilityRequest(requestId, 100L, LocalDate.parse("2026-01-10"),
                        LocalDate.parse("2026-01-12")));

        assertThat(response.status()).isEqualTo("HELD");
        server.verify(); // все 3 попытки реально сделаны
    }

    @Test
    void doesNotRetryBusinessConflict409() {
        server.expect(requestTo(HOTEL_URL + "6/confirm-availability"))
                .andRespond(withStatus(CONFLICT)
                        .body("{\"message\":\"Номер занят\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.confirmAvailability(6L,
                new ConfirmAvailabilityRequest(UUID.randomUUID(), 100L,
                        LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-12"))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Номер занят");
        server.verify();
    }

    @Test
    void networkFailureExhaustsRetriesAndThrowsIntegrationException() {
        server.expect(requestTo(HOTEL_URL + "7/confirm-availability"))
                .andRespond(withException(new java.net.ConnectException("connection refused")));
        server.expect(requestTo(HOTEL_URL + "7/confirm-availability"))
                .andRespond(withException(new java.net.ConnectException("connection refused")));
        server.expect(requestTo(HOTEL_URL + "7/confirm-availability"))
                .andRespond(withException(new java.net.ConnectException("connection refused")));

        assertThatThrownBy(() -> client.confirmAvailability(7L,
                new ConfirmAvailabilityRequest(UUID.randomUUID(), 100L,
                        LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-12"))))
                .isInstanceOf(IntegrationException.class);
        server.verify();
    }
}
