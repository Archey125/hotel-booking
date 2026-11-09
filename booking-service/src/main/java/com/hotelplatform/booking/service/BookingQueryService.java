package com.hotelplatform.booking.service;

import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.booking.repository.BookingSpecifications;
import com.hotelplatform.common.exceptions.NotFoundException;
import com.hotelplatform.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public Page<Booking> listOwn(Long userId, BookingStatus status, LocalDate dateFrom, LocalDate dateTo,
                                 Pageable pageable) {
        return bookingRepository.findAll(spec(userId, status, dateFrom, dateTo), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> listAll(BookingStatus status, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        return bookingRepository.findAll(spec(null, status, dateFrom, dateTo), pageable);
    }

    @Transactional(readOnly = true)
    public Booking getAccessible(Long callerUserId, Long bookingId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронирование", bookingId));
        if (!isAdmin && !booking.getUserId().equals(callerUserId)) {
            throw new AccessDeniedException("Доступны только свои бронирования");
        }
        return booking;
    }

    public static Specification<Booking> spec(Long userId, BookingStatus status,
                                              LocalDate dateFrom, LocalDate dateTo) {
        return BookingSpecifications.userId(userId)
                .and(BookingSpecifications.status(status))
                .and(BookingSpecifications.overlaps(dateFrom, dateTo));
    }

    public static <T> PageResponse<T> toPage(Page<T> page) {
        return PageResponse.of(page);
    }
}
