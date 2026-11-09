package com.hotelplatform.hotel;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HotelRoomCrudIT extends HotelIntegrationTest {

    @Test
    void adminCreatesHotelAndRoom() throws Exception {
        String hotel = mockMvc.perform(post("/api/hotels")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"name\":\"Test Hotel\",\"address\":\"ул. Ленина, 5\",\"city\":\"Казань\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();
        long hotelId = objectMapper.readTree(hotel).get("id").asLong();

        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"hotelId\":" + hotelId + ",\"number\":\"777\",\"pricePerNight\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.timesBooked").value(0));

        // дубликат номера в том же отеле -> 409
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"hotelId\":" + hotelId + ",\"number\":\"777\"}"))
                .andExpect(status().isConflict());

        // несуществующий отель -> 404
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"hotelId\":99999,\"number\":\"888\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crudRequiresAdminRole() throws Exception {
        // без токена — 401
        mockMvc.perform(post("/api/hotels")
                        .contentType("application/json").content("{\"name\":\"X\",\"address\":\"Y\"}"))
                .andExpect(status().isUnauthorized());

        // USER не может создавать/удалять — 403
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", userToken())
                        .contentType("application/json").content("{\"name\":\"X\",\"address\":\"Y\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/hotels/1").header("Authorization", userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListAndFilterHotels() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"name\":\"Moscow Grand\",\"address\":\"Тверская 1\",\"city\":\"Москва\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/hotels")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"name\":\"Kazan Plaza\",\"address\":\"Баумана 2\",\"city\":\"Казань\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/hotels").header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/hotels?city=москва").header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Moscow Grand"));
    }

    @Test
    void updateAndDeleteRoom() throws Exception {
        long hotelId = objectMapper.readTree(mockMvc.perform(post("/api/hotels")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"name\":\"H\",\"address\":\"A\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        long roomId = objectMapper.readTree(mockMvc.perform(post("/api/rooms")
                        .header("Authorization", adminToken())
                        .contentType("application/json")
                        .content("{\"hotelId\":" + hotelId + ",\"number\":\"1\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // вывод в ремонт: available=false
        mockMvc.perform(patch("/api/rooms/" + roomId)
                        .header("Authorization", adminToken())
                        .contentType("application/json").content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/rooms/" + roomId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/" + roomId).header("Authorization", userToken()))
                .andExpect(status().isNotFound());
    }
}
