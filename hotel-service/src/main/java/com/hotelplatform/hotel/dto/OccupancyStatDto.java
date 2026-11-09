package com.hotelplatform.hotel.dto;

public record OccupancyStatDto(
        Long hotelId,
        String hotelName,
        Long roomId,
        String roomNumber,
        boolean available,
        int timesBooked,
        long activeHolds,
        long heldDaysInPeriod
) {
}
