package com.hotelplatform.hotel.repository;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class HotelSpecifications {

    private HotelSpecifications() {
    }

    public static Specification<Hotel> nameLike(String name) {
        return (root, query, cb) -> name == null || name.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Hotel> cityLike(String city) {
        return (root, query, cb) -> city == null || city.isBlank()
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("city")), "%" + city.toLowerCase() + "%");
    }

    public static Specification<Room> rooms() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Room> roomHotelId(Long hotelId) {
        return (root, query, cb) -> hotelId == null
                ? cb.conjunction()
                : cb.equal(root.get("hotel").get("id"), hotelId);
    }

    public static Specification<Room> roomAvailable(Boolean available) {
        return (root, query, cb) -> available == null
                ? cb.conjunction()
                : cb.equal(root.get("available"), available);
    }

    public static Specification<Room> roomPriceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            if (minPrice != null) {
                parts.add(cb.greaterThanOrEqualTo(root.get("pricePerNight"), minPrice));
            }
            if (maxPrice != null) {
                parts.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), maxPrice));
            }
            return parts.isEmpty() ? cb.conjunction() : cb.and(parts.toArray(Predicate[]::new));
        };
    }

    public static Specification<Room> roomIdNotIn(Collection<Long> excludedIds) {
        return (root, query, cb) -> excludedIds == null || excludedIds.isEmpty()
                ? cb.conjunction()
                : root.get("id").in(excludedIds).not();
    }
}
