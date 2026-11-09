package com.hotelplatform.hotel.repository;

import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomLockRepository extends JpaRepository<RoomLock, Long> {

    Optional<RoomLock> findByRoomIdAndRequestId(Long roomId, UUID requestId);

    Optional<RoomLock> findByRequestId(UUID requestId);

    // пересечение интервалов по правилу [start, end): живой лок с захлёстом дат
    @Query("""
            select count(l) > 0 from RoomLock l
            where l.room.id = :roomId and l.status = :status and l.expiresAt > :now
              and l.startDate < :end and l.endDate > :start
            """)
    boolean existsOverlappingLock(@Param("roomId") Long roomId,
                                  @Param("status") RoomLockStatus status,
                                  @Param("start") LocalDate start,
                                  @Param("end") LocalDate end,
                                  @Param("now") Instant now);

    @Query("""
            select l.room.id from RoomLock l
            where l.status = :status and l.expiresAt > :now and l.startDate < :end and l.endDate > :start
            """)
    List<Long> findLockedRoomIds(@Param("status") RoomLockStatus status,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end,
                                 @Param("now") Instant now);

    List<RoomLock> findByStatusAndExpiresAtBefore(RoomLockStatus status, Instant now);

    List<RoomLock> findByRoomIdAndStatus(Long roomId, RoomLockStatus status);

    void deleteByRoomIdIn(Collection<Long> roomIds);

    // строку лока тоже блокируем, чтобы два release не гонялись
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from RoomLock l where l.room.id = :roomId and l.requestId = :requestId")
    Optional<RoomLock> findByRoomIdAndRequestIdForUpdate(@Param("roomId") Long roomId,
                                                         @Param("requestId") UUID requestId);
}
