package com.hotelplatform.hotel.service;

import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import com.hotelplatform.common.api.ReleaseRequest;
import com.hotelplatform.common.api.ReleaseResponse;
import com.hotelplatform.common.exceptions.BadRequestException;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.NotFoundException;
import com.hotelplatform.hotel.config.SagaProperties;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.repository.RoomLockRepository;
import com.hotelplatform.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// шаги саги на стороне hotel-service: confirm ставит блокировку слота, release снимает
// идемпотентность по requestId (уникальный индекс room_id + request_id)
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomLockService {

    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;
    private final SagaProperties sagaProperties;

    @Transactional
    public ConfirmAvailabilityResponse confirm(Long roomId, ConfirmAvailabilityRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new BadRequestException("startDate должна быть раньше endDate");
        }
        // SELECT FOR UPDATE: конкурентный confirm того же номера подождёт и увидит наш лок
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Номер", roomId));
        if (!room.isAvailable()) {
            throw new ConflictException("Номер id=%d выведен из эксплуатации (available=false)".formatted(roomId));
        }

        var existing = roomLockRepository.findByRoomIdAndRequestId(roomId, request.requestId());
        if (existing.isPresent()) {
            RoomLock lock = existing.get();
            if (lock.getStatus() == RoomLockStatus.HELD) {
                log.info("[saga] идемпотентный повтор confirm: roomId={} requestId={} уже HELD",
                        roomId, request.requestId());
                return toResponse(lock);
            }
            throw new ConflictException(
                    "requestId=%s уже обработан со статусом %s".formatted(request.requestId(), lock.getStatus()));
        }

        if (roomLockRepository.existsOverlappingLock(roomId, RoomLockStatus.HELD,
                request.startDate(), request.endDate(), Instant.now())) {
            log.info("[saga] отказ confirm: номер id={} занят на {}..{}", roomId,
                    request.startDate(), request.endDate());
            throw new ConflictException("Номер id=%d уже занят на даты %s..%s"
                    .formatted(roomId, request.startDate(), request.endDate()));
        }

        RoomLock lock = RoomLock.builder()
                .room(room)
                .requestId(request.requestId())
                .bookingId(request.bookingId())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(RoomLockStatus.HELD)
                .expiresAt(Instant.now().plus(sagaProperties.lockTtlMinutes(), ChronoUnit.MINUTES))
                .build();
        roomLockRepository.save(lock);
        room.setTimesBooked(room.getTimesBooked() + 1);

        log.info("[saga] confirm OK: roomId={} requestId={} bookingId={} slot={}/{} timesBooked={}",
                roomId, request.requestId(), request.bookingId(),
                request.startDate(), request.endDate(), room.getTimesBooked());
        return toResponse(lock);
    }

    @Transactional
    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        var lockOpt = roomLockRepository.findByRoomIdAndRequestIdForUpdate(roomId, request.requestId());
        if (lockOpt.isEmpty()) {
            log.info("[saga] release: блокировка roomId={} requestId={} не найдена — идемпотентный no-op",
                    roomId, request.requestId());
            return new ReleaseResponse(request.requestId(), "ALREADY_RELEASED");
        }
        RoomLock lock = lockOpt.get();
        if (lock.getStatus() != RoomLockStatus.HELD) {
            log.info("[saga] release: блокировка уже в статусе {} — no-op", lock.getStatus());
            return new ReleaseResponse(request.requestId(), "ALREADY_RELEASED");
        }
        lock.setStatus(RoomLockStatus.RELEASED);
        Room room = lock.getRoom();
        room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
        log.info("[saga] release OK (компенсация): roomId={} requestId={} bookingId={} -> RELEASED, timesBooked={}",
                roomId, request.requestId(), request.bookingId(), room.getTimesBooked());
        return new ReleaseResponse(request.requestId(), "RELEASED");
    }

    @Scheduled(fixedDelayString = "${app.saga.sweeper-interval-ms:60000}", initialDelay = 60000)
    @Transactional
    public int expireStaleLocks() {
        List<RoomLock> stale = roomLockRepository.findByStatusAndExpiresAtBefore(
                RoomLockStatus.HELD, Instant.now());
        for (RoomLock lock : stale) {
            lock.setStatus(RoomLockStatus.EXPIRED);
            Room room = lock.getRoom();
            room.setTimesBooked(Math.max(0, room.getTimesBooked() - 1));
            log.warn("[saga] свипер: TTL блокировки истёк — EXPIRED roomId={} requestId={} bookingId={}",
                    room.getId(), lock.getRequestId(), lock.getBookingId());
        }
        return stale.size();
    }

    private ConfirmAvailabilityResponse toResponse(RoomLock lock) {
        return new ConfirmAvailabilityResponse(lock.getRequestId(), lock.getRoom().getId(), lock.getId(),
                lock.getStatus().name(), lock.getStartDate(), lock.getEndDate());
    }
}
