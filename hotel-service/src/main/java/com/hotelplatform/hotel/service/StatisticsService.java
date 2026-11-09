package com.hotelplatform.hotel.service;

import com.hotelplatform.hotel.dto.OccupancyStatDto;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.repository.RoomLockRepository;
import com.hotelplatform.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

// статистика загруженности номеров для админа
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;

    @Transactional(readOnly = true)
    public List<OccupancyStatDto> occupancy(Long hotelId, LocalDate dateFrom, LocalDate dateTo) {
        List<Room> rooms = hotelId == null
                ? roomRepository.findAll()
                : roomRepository.findAllByHotelId(hotelId);
        return rooms.stream()
                .map(room -> toStat(room, dateFrom, dateTo))
                .sorted(Comparator.comparingLong(OccupancyStatDto::timesBooked).reversed()
                        .thenComparing(OccupancyStatDto::roomId))
                .toList();
    }

    private OccupancyStatDto toStat(Room room, LocalDate from, LocalDate to) {
        List<RoomLock> held = roomLockRepository.findByRoomIdAndStatus(room.getId(), RoomLockStatus.HELD)
                .stream()
                .filter(l -> l.getExpiresAt().isAfter(Instant.now()))
                .toList();
        long activeHolds = held.size();
        long heldDays = held.stream()
                .mapToLong(lock -> overlapDays(lock, from, to))
                .sum();
        return new OccupancyStatDto(
                room.getHotel().getId(),
                room.getHotel().getName(),
                room.getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked(),
                activeHolds,
                heldDays);
    }

    private long overlapDays(RoomLock lock, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? lock.getStartDate() : lock.getStartDate().isBefore(from) ? from : lock.getStartDate();
        LocalDate end = to == null ? lock.getEndDate() : lock.getEndDate().isAfter(to) ? to : lock.getEndDate();
        long days = ChronoUnit.DAYS.between(start, end);
        return Math.max(0, days);
    }
}
