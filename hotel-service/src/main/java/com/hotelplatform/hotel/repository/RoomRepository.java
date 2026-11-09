package com.hotelplatform.hotel.repository;

import com.hotelplatform.hotel.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    // SELECT FOR UPDATE для confirm в саге: конкурентные подтверждения строятся в очередь
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);

    long countByHotelId(Long hotelId);

    List<Room> findAllByHotelId(Long hotelId);

    Optional<Room> findByHotelIdAndNumber(Long hotelId, String number);
}
