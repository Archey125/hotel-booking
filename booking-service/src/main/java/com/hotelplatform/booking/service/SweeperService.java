package com.hotelplatform.booking.service;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.config.BookingSagaProperties;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.common.api.ReleaseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

// ищет зависшие PENDING и отменяет их - на случай если сервис упал между шагами саги
@Service
@RequiredArgsConstructor
@Slf4j
public class SweeperService {

    private final BookingRepository bookingRepository;
    private final BookingStateService stateService;
    private final HotelClient hotelClient;
    private final BookingSagaProperties sagaProperties;

    @Scheduled(fixedDelayString = "${app.saga.sweeper-interval-ms:30000}", initialDelay = 30000)
    public int cancelStalePending() {
        Instant cutoff = Instant.now().minusSeconds(sagaProperties.pendingTimeoutSeconds());
        List<Booking> stale = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, cutoff);
        for (Booking booking : stale) {
            log.warn("[saga] свипер: PENDING старше {} с — отменяю bookingId={} requestId={}",
                    sagaProperties.pendingTimeoutSeconds(), booking.getId(), booking.getRequestId());
            stateService.markCancelled(booking.getId(), "истекло ожидание подтверждения (свипер)");
            try {
                hotelClient.release(booking.getRoomId(),
                        new ReleaseRequest(booking.getRequestId(), booking.getId()));
            } catch (RuntimeException e) {
                log.error("[saga] свипер: release не удался для bookingId={} — слот освободит TTL в Hotel Service",
                        booking.getId());
            }
        }
        return stale.size();
    }
}
