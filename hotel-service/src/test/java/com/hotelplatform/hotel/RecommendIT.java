package com.hotelplatform.hotel;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecommendIT extends HotelIntegrationTest {

    @Autowired
    RoomRepository roomRepository;

    @Test
    void recommendSortsByTimesBookedAscThenId() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Rec Hotel").address("A").build());
        Room busy = roomRepository.save(Room.builder().hotel(hotel).number("1").timesBooked(7).build());
        Room repair = roomRepository.save(Room.builder().hotel(hotel).number("2").timesBooked(0).available(false).build());
        Room free = roomRepository.save(Room.builder().hotel(hotel).number("3").timesBooked(0).build());
        Room middle = roomRepository.save(Room.builder().hotel(hotel).number("4").timesBooked(3).build());

        String response = mockMvc.perform(get("/api/rooms/recommend").header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> ids = objectMapper.readTree(response)
                .valueStream().map(n -> n.get("id").asLong()).toList();

        assertThat(ids).containsExactly(free.getId(), middle.getId(), busy.getId());
        assertThat(ids).doesNotContain(repair.getId());
    }

    @Test
    void tieBreakIsByIdAsc() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Tie Hotel").address("A").build());
        Room lowerId = roomRepository.save(Room.builder().hotel(hotel).number("10").timesBooked(2).build());
        Room higherId = roomRepository.save(Room.builder().hotel(hotel).number("11").timesBooked(2).build());

        String response = mockMvc.perform(get("/api/rooms/recommend").header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = objectMapper.readTree(response)
                .valueStream().map(n -> n.get("id").asLong()).toList();

        // при равенстве times_booked порядок строго по возрастанию id
        assertThat(ids).containsExactly(lowerId.getId(), higherId.getId());
    }

    @Test
    void recommendExcludesRoomsHeldForRequestedDates() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Date Hotel").address("A").build());
        Room held = roomRepository.save(Room.builder().hotel(hotel).number("1").build());
        Room free = roomRepository.save(Room.builder().hotel(hotel).number("2").build());

        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(2);
        roomLockRepository.save(RoomLock.builder()
                .room(held).requestId(UUID.randomUUID()).bookingId(1L)
                .startDate(start).endDate(end)
                .status(RoomLockStatus.HELD)
                .expiresAt(Instant.now().plusSeconds(600))
                .build());

        // с датами: заблокированный номер не предлагается
        String withDates = mockMvc.perform(get("/api/rooms/recommend")
                        .header("Authorization", userToken())
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(withDates).contains("\"id\":" + free.getId());
        assertThat(withDates).doesNotContain("\"id\":" + held.getId());

        // без дат: возвращаются все операционно доступные
        String withoutDates = mockMvc.perform(get("/api/rooms/recommend")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(withoutDates).contains(String.valueOf(held.getId()));
    }

    @Test
    void freeRoomsListSupportsFiltersAndSort() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Filter Hotel").address("A").build());
        roomRepository.save(Room.builder().hotel(hotel).number("1")
                .pricePerNight(new BigDecimal("3000")).build());
        roomRepository.save(Room.builder().hotel(hotel).number("2")
                .pricePerNight(new BigDecimal("9000")).build());

        mockMvc.perform(get("/api/rooms").header("Authorization", userToken())
                        .param("maxPrice", "5000")
                        .param("sort", "pricePerNight,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pricePerNight").value(3000));

        mockMvc.perform(get("/api/rooms").header("Authorization", userToken())
                        .param("sort", "pricePerNight,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].pricePerNight").value(9000));
    }
}
