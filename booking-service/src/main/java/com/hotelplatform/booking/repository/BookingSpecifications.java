package com.hotelplatform.booking.repository;

import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    public static Specification<Booking> userId(Long userId) {
        return (root, query, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Booking> status(BookingStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Booking> overlaps(LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            if (dateFrom != null) {
                parts.add(cb.greaterThanOrEqualTo(root.get("endDate"), dateFrom));
            }
            if (dateTo != null) {
                parts.add(cb.lessThanOrEqualTo(root.get("startDate"), dateTo));
            }
            return parts.isEmpty() ? cb.conjunction() : cb.and(parts.toArray(Predicate[]::new));
        };
    }
}
