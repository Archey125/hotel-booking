package com.hotelplatform.hotel.service;

import com.hotelplatform.common.exceptions.BadRequestException;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.NotFoundException;
import com.hotelplatform.hotel.dto.RoomCreateRequest;
import com.hotelplatform.hotel.dto.RoomDto;
import com.hotelplatform.hotel.dto.RoomUpdateRequest;
import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.mapper.RoomMapper;
import com.hotelplatform.hotel.repository.HotelRepository;
import com.hotelplatform.hotel.repository.HotelSpecifications;
import com.hotelplatform.hotel.repository.RoomLockRepository;
import com.hotelplatform.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

// CRUD номеров + выдача свободных и рекомендаций
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomDto create(RoomCreateRequest request) {
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new NotFoundException("Отель", request.hotelId()));
        roomRepository.findByHotelIdAndNumber(hotel.getId(), request.number()).ifPresent(r -> {
            throw new ConflictException("Номер %s уже существует в отеле id=%d".formatted(request.number(), hotel.getId()));
        });
        Room room = Room.builder()
                .hotel(hotel)
                .number(request.number())
                .available(request.available() == null || request.available())
                .pricePerNight(request.pricePerNight())
                .build();
        Room saved = roomRepository.save(room);
        log.info("Создан номер id={} number={} hotelId={}", saved.getId(), saved.getNumber(), hotel.getId());
        return roomMapper.toDto(saved);
    }

    @Transactional
    public RoomDto update(Long id, RoomUpdateRequest request) {
        Room room = getEntity(id);
        if (request.number() != null) {
            roomRepository.findByHotelIdAndNumber(room.getHotel().getId(), request.number())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("Номер %s уже существует в этом отеле".formatted(request.number()));
                    });
            room.setNumber(request.number());
        }
        if (request.available() != null) {
            room.setAvailable(request.available());
        }
        if (request.pricePerNight() != null) {
            room.setPricePerNight(request.pricePerNight());
        }
        log.info("Обновлён номер id={}", id);
        return roomMapper.toDto(room);
    }

    @Transactional
    public void delete(Long id) {
        Room room = getEntity(id);
        if (roomLockRepository.existsOverlappingLock(id, RoomLockStatus.HELD,
                LocalDate.MIN, LocalDate.MAX, Instant.now())) {
            throw new ConflictException("Нельзя удалить номер: есть активные блокировки слота");
        }
        roomRepository.delete(room);
        log.info("Удалён номер id={}", id);
    }

    @Transactional(readOnly = true)
    public RoomDto get(Long id) {
        return roomMapper.toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<RoomDto> listFree(Long hotelId, LocalDate startDate, LocalDate endDate,
                                  BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        Specification<Room> spec = HotelSpecifications.rooms()
                .and(HotelSpecifications.roomHotelId(hotelId))
                .and(HotelSpecifications.roomAvailable(true))
                .and(HotelSpecifications.roomPriceBetween(minPrice, maxPrice));
        if (startDate != null && endDate != null) {
            validateRange(startDate, endDate);
            List<Long> locked = roomLockRepository.findLockedRoomIds(
                    RoomLockStatus.HELD, startDate, endDate, Instant.now());
            spec = spec.and(HotelSpecifications.roomIdNotIn(locked));
        }
        return roomRepository.findAll(spec, pageable).map(roomMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<RoomDto> recommend(Long hotelId, LocalDate startDate, LocalDate endDate) {
        // свободные на даты, сортировка times_booked ASC, при равенстве - по id
        Specification<Room> spec = HotelSpecifications.rooms()
                .and(HotelSpecifications.roomHotelId(hotelId))
                .and(HotelSpecifications.roomAvailable(true));
        if (startDate != null && endDate != null) {
            validateRange(startDate, endDate);
            // занятые на пересекающиеся даты номера просто выкидываем из выборки
            List<Long> locked = roomLockRepository.findLockedRoomIds(
                    RoomLockStatus.HELD, startDate, endDate, Instant.now());
            spec = spec.and(HotelSpecifications.roomIdNotIn(locked));
        }
        return roomRepository.findAll(spec).stream()
                .sorted(Comparator.comparingInt(Room::getTimesBooked)
                        .thenComparing(Room::getId))
                .map(roomMapper::toDto)
                .toList();
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            throw new BadRequestException(
                    "startDate должна быть раньше endDate (даты в формате [start, end))");
        }
    }

    private Room getEntity(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Номер", id));
    }
}
