package com.hotelplatform.hotel;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.entity.RoomLock;
import com.hotelplatform.hotel.entity.RoomLockStatus;
import com.hotelplatform.hotel.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatisticsIT extends HotelIntegrationTest {

    @Autowired
    RoomRepository roomRepository;

    @Test
    void adminGetsOccupancyStatisticsSortedByTimesBookedDesc() throws Exception {
        Hotel hotel = hotelRepository.save(Hotel.builder().name("Stat Hotel").address("A").build());
        roomRepository.save(Room.builder().hotel(hotel).number("1").timesBooked(5).build());
        Room room2 = roomRepository.save(Room.builder().hotel(hotel).number("2").timesBooked(9).build());

        roomLockRepository.save(RoomLock.builder()
                .room(room2).requestId(UUID.randomUUID()).bookingId(1L)
                .startDate(LocalDate.now().plusDays(1)).endDate(LocalDate.now().plusDays(3))
                .status(RoomLockStatus.HELD)
                .expiresAt(Instant.now().plusSeconds(600))
                .build());

        mockMvc.perform(get("/api/statistics/occupancy").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(room2.getId()))
                .andExpect(jsonPath("$[0].timesBooked").value(9))
                .andExpect(jsonPath("$[0].activeHolds").value(1))
                .andExpect(jsonPath("$[0].heldDaysInPeriod").value(2))
                .andExpect(jsonPath("$[1].timesBooked").value(5));

        // фильтр по периоду: блокировка на [+1,+3) пересекается с [+1,+2) одними сутками
        mockMvc.perform(get("/api/statistics/occupancy")
                        .header("Authorization", adminToken())
                        .param("dateFrom", LocalDate.now().plusDays(1).toString())
                        .param("dateTo", LocalDate.now().plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].heldDaysInPeriod").value(1));
    }

    @Test
    void statisticsIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/statistics/occupancy").header("Authorization", userToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/statistics/occupancy"))
                .andExpect(status().isUnauthorized());
    }
}
