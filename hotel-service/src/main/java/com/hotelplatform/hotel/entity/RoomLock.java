package com.hotelplatform.hotel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_locks",
        uniqueConstraints = @UniqueConstraint(name = "uk_locks_room_request", columnNames = {"room_id", "request_id"}),
        indexes = {
                @Index(name = "idx_locks_room_dates", columnList = "room_id,start_date,end_date"),
                @Index(name = "idx_locks_expires", columnList = "status,expires_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // лок слота на даты [startDate, endDate); HELD -> RELEASED/EXPIRED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomLockStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // TTL: если booking-service упал и не сделал release, лок снимет свипер
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
