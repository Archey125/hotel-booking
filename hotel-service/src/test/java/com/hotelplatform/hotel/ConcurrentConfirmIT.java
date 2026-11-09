package com.hotelplatform.hotel;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ConcurrentConfirmIT extends HotelIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    private final LocalDate start = LocalDate.now().plusDays(1);
    private final LocalDate end = LocalDate.now().plusDays(3);

    private String internalTokenHeader;
    private Long roomId;

    @BeforeEach
    void seed() {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Concurrent Hotel").address("A").build());
        Room room = roomRepository.save(Room.builder().hotel(hotel).number("999").build());
        roomId = room.getId();
        internalTokenHeader = internalToken();
    }

    @Test
    void onlyOneConcurrentBookingWinsTheSlot() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();

        List<Callable<ResponseEntity<String>>> tasks = IntStream.range(0, threads)
                .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(internalTokenHeader.replace("Bearer ", ""));
                    String body = """
                            {"requestId":"%s","bookingId":%d,"startDate":"%s","endDate":"%s"}
                            """.formatted(UUID.randomUUID(), 100 + i, start, end);
                    return rest.postForEntity("/api/rooms/" + roomId + "/confirm-availability",
                            new HttpEntity<>(body, headers), String.class);
                })
                .toList();

        var futures = pool.invokeAll(tasks);
        for (var future : futures) {
            ResponseEntity<String> response = future.get();
            if (response.getStatusCode().value() == 200) {
                successes.incrementAndGet();
            } else if (response.getStatusCode().value() == 409) {
                conflicts.incrementAndGet();
            }
        }
        pool.shutdown();

        assertThat(successes.get()).as("ровно одна победа в гонке").isEqualTo(1);
        assertThat(conflicts.get()).as("остальные получают 409").isEqualTo(threads - 1);

        Room room = roomRepository.findById(roomId).orElseThrow();
        assertThat(room.getTimesBooked()).as("times_booked инкрементирован ровно один раз").isEqualTo(1);
        assertThat(roomLockRepository.findByRoomIdAndStatus(roomId, RoomLockStatus.HELD))
                .as("ровно одна блокировка слота").hasSize(1);
    }
}
