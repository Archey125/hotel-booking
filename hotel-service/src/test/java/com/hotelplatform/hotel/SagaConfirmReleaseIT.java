package com.hotelplatform.hotel;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.repository.RoomRepository;
import com.hotelplatform.hotel.service.RoomLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SagaConfirmReleaseIT extends HotelIntegrationTest {

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomLockService roomLockService;

    private Long roomId;
    private Room room;

    private final LocalDate start = LocalDate.now().plusDays(1);
    private final LocalDate end = LocalDate.now().plusDays(3);

    @BeforeEach
    void seedRoom() {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Saga Hotel").address("A").city("C").build());
        room = roomRepository.save(Room.builder().hotel(hotel).number("100").build());
        roomId = room.getId();
    }

    @Test
    void confirmHoldsSlotAndIncrementsTimesBooked() throws Exception {
        String request = confirmBody(UUID.randomUUID(), 500L);

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.roomId").value(roomId));

        assertThat(roomRepository.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
        assertThat(roomLockRepository.findByRoomIdAndStatus(roomId, RoomLockStatus.HELD)).hasSize(1);
    }

    @Test
    void repeatedConfirmWithSameRequestIdIsIdempotent() throws Exception {
        UUID requestId = UUID.randomUUID();
        String request = confirmBody(requestId, 501L);

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk());

        // повторная доставка того же requestId: успех без повторного инкремента
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));

        assertThat(roomRepository.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
        assertThat(roomLockRepository.findAll()).hasSize(1);
    }

    @Test
    void overlappingConfirmIsRejectedWith409() throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(confirmBody(UUID.randomUUID(), 1L)))
                .andExpect(status().isOk());

        // другой requestId, пересекающиеся даты -> 409, без создания блокировки
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(confirmBody(UUID.randomUUID(), 2L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Номер id=%d уже занят на даты %s..%s"
                        .formatted(roomId, start, end)));

        assertThat(roomLockRepository.findByRoomIdAndStatus(roomId, RoomLockStatus.HELD)).hasSize(1);
        assertThat(roomRepository.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
    }

    @Test
    void backToBackDatesDoNotOverlap() throws Exception {
        // [start, end): выезд 3-го числа не мешает заезду 3-го числа
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(confirmBody(UUID.randomUUID(), 1L)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json")
                        .content(confirmBody(UUID.randomUUID(), 2L, end, end.plusDays(2))))
                .andExpect(status().isOk());

        assertThat(roomLockRepository.findByRoomIdAndStatus(roomId, RoomLockStatus.HELD)).hasSize(2);
    }

    @Test
    void releaseCompensatesAndIsIdempotent() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(confirmBody(requestId, 10L)))
                .andExpect(status().isOk());

        // release после неудачи саги
        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .header("Authorization", internalToken())
                        .contentType("application/json")
                        .content("{\"requestId\":\"" + requestId + "\",\"bookingId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        assertThat(roomRepository.findById(roomId).orElseThrow().getTimesBooked()).isZero();

        // повторный release — идемпотентный no-op
        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .header("Authorization", internalToken())
                        .contentType("application/json")
                        .content("{\"requestId\":\"" + requestId + "\",\"bookingId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_RELEASED"));

        // release по неизвестному requestId — тоже no-op (не ломает компенсацию)
        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .header("Authorization", internalToken())
                        .contentType("application/json")
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_RELEASED"));
    }

    @Test
    void internalEndpointsRejectNonInternalRoles() throws Exception {
        // USER не может вызывать шаги саги — 403
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", userToken())
                        .contentType("application/json").content(confirmBody(UUID.randomUUID(), 1L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .header("Authorization", userToken())
                        .contentType("application/json").content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());

        // без токена — 401
        mockMvc.perform(post("/api/rooms/" + roomId + "/release")
                        .contentType("application/json").content("{\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmRejectsBadDatesAndUnknownRoom() throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json")
                        .content(confirmBody(UUID.randomUUID(), 1L, end, start)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/rooms/99999/confirm-availability")
                        .header("Authorization", internalToken())
                        .contentType("application/json").content(confirmBody(UUID.randomUUID(), 1L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sweeperExpiresStaleHeldLocks() {
        UUID requestId = UUID.randomUUID();
        RoomLock lock = RoomLock.builder()
                .room(room).requestId(requestId).bookingId(77L)
                .startDate(start).endDate(end)
                .status(RoomLockStatus.HELD)
                .expiresAt(Instant.now().minusSeconds(60)) // TTL истёк
                .build();
        roomLockRepository.save(lock);
        room.setTimesBooked(1);
        roomRepository.save(room);

        // вызов через Spring-прокси, чтобы работала @Transactional
        int expired = roomLockService.expireStaleLocks();

        assertThat(expired).isEqualTo(1);
        RoomLock after = roomLockRepository.findByRequestId(requestId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RoomLockStatus.EXPIRED);
        assertThat(roomRepository.findById(roomId).orElseThrow().getTimesBooked()).isZero();
    }

    private String confirmBody(UUID requestId, Long bookingId) {
        return confirmBody(requestId, bookingId, start, end);
    }

    private String confirmBody(UUID requestId, Long bookingId, LocalDate s, LocalDate e) {
        return """
                {"requestId":"%s","bookingId":%d,"startDate":"%s","endDate":"%s"}
                """.formatted(requestId, bookingId, s, e);
    }
}
