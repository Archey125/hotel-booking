package com.hotelplatform.booking;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.booking.service.SweeperService;
import com.hotelplatform.common.api.ReleaseResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SweeperServiceTest {

    @Autowired
    SweeperService sweeperService;
    @Autowired
    BookingRepository bookingRepository;
    @MockitoBean
    HotelClient hotelClient;

    @Test
    void stalePendingIsCancelledAndReleased() {
        Booking stale = bookingRepository.save(Booking.builder()
                .userId(1L).roomId(5L)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(2))
                .status(BookingStatus.PENDING)
                .requestId(UUID.randomUUID())
                .createdAt(Instant.now().minusSeconds(3600)) // старше pending-timeout
                .build());
        Booking fresh = bookingRepository.save(Booking.builder()
                .userId(1L).roomId(6L)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(2))
                .status(BookingStatus.PENDING)
                .requestId(UUID.randomUUID())
                .build());

        when(hotelClient.release(any(), any()))
                .thenReturn(new ReleaseResponse(stale.getRequestId(), "RELEASED"));

        int cancelled = sweeperService.cancelStalePending();

        assertThat(cancelled).isGreaterThanOrEqualTo(1);
        assertThat(bookingRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
        verify(hotelClient).release(eq(5L), any());
    }
}
