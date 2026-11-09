package com.hotelplatform.hotel.service;

import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.NotFoundException;
import com.hotelplatform.hotel.dto.HotelCreateRequest;
import com.hotelplatform.hotel.dto.HotelDto;
import com.hotelplatform.hotel.dto.HotelUpdateRequest;
import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.mapper.HotelMapper;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;
    private final HotelMapper hotelMapper;

    @Transactional
    public HotelDto create(HotelCreateRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        Hotel saved = hotelRepository.save(hotel);
        log.info("Создан отель id={} name={}", saved.getId(), saved.getName());
        return hotelMapper.toDto(saved);
    }

    @Transactional
    public HotelDto update(Long id, HotelUpdateRequest request) {
        Hotel hotel = getEntity(id);
        hotelMapper.update(request, hotel);
        log.info("Обновлён отель id={}", id);
        return hotelMapper.toDto(hotel);
    }

    @Transactional
    public void delete(Long id) {
        Hotel hotel = getEntity(id);
        boolean hasActiveHolds = roomRepository.findAllByHotelId(id).stream()
                .anyMatch(room -> roomLockRepository.existsOverlappingLock(
                        room.getId(), RoomLockStatus.HELD,
                        java.time.LocalDate.MIN, java.time.LocalDate.MAX, java.time.Instant.now()));
        if (hasActiveHolds) {
            throw new ConflictException("Нельзя удалить отель: есть активные блокировки слотов");
        }
        roomLockRepository.deleteByRoomIdIn(hotel.getRooms().stream().map(r -> r.getId()).toList());
        roomRepository.deleteAll(hotel.getRooms());
        hotelRepository.delete(hotel);
        log.info("Удалён отель id={} (с номерами)", id);
    }

    @Transactional(readOnly = true)
    public HotelDto get(Long id) {
        return hotelMapper.toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<HotelDto> list(String name, String city, Pageable pageable) {
        Specification<Hotel> spec = HotelSpecifications.nameLike(name).and(HotelSpecifications.cityLike(city));
        return hotelRepository.findAll(spec, pageable).map(hotelMapper::toDto);
    }

    private Hotel getEntity(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отель", id));
    }
}
