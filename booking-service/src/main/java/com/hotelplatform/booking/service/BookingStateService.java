package com.hotelplatform.booking.service;

import com.hotelplatform.booking.dto.CreateBookingRequest;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.common.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// все переходы статуса брони в одном месте, каждый переход - своя короткая транзакция
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingStateService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking markPending(UserAccount user, Long roomId, CreateBookingRequest request, UUID requestId) {
        Booking booking = Booking.builder()
                .userId(user.getId())
                .roomId(roomId)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(BookingStatus.PENDING)
                .requestId(requestId)
                .build();
        Booking saved = bookingRepository.saveAndFlush(booking);
        log.info("[saga] PENDING зафиксировано: bookingId={} userId={} roomId={} requestId={} даты {}/{}",
                saved.getId(), saved.getUserId(), saved.getRoomId(), requestId,
                saved.getStartDate(), saved.getEndDate());
        return saved;
    }

    @Transactional
    public Booking markConfirmed(Long bookingId) {
        Booking booking = getEntity(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            log.info("[saga] повторный confirm для bookingId={} — уже CONFIRMED, идемпотентный no-op", bookingId);
            return booking;
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        log.info("[saga] переход PENDING -> CONFIRMED: bookingId={}", bookingId);
        return booking;
    }

    @Transactional
    public Booking markCancelled(Long bookingId, String reason) {
        Booking booking = getEntity(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("[saga] bookingId={} уже CANCELLED — идемпотентный no-op ({})", bookingId, reason);
            return booking;
        }
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        log.info("[saga] переход {} -> CANCELLED: bookingId={} причина: {}",
                previous, bookingId, reason);
        return booking;
    }

    private Booking getEntity(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Бронирование", id));
    }
}
