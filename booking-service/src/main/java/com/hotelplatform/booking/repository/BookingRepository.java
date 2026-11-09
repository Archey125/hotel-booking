package com.hotelplatform.booking.repository;

import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByRequestId(UUID requestId);

    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, Instant cutoff);

    void deleteByUserId(Long userId);
}
