package com.hotelplatform.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelplatform.common.security.JwtTokens;
import com.hotelplatform.hotel.repository.HotelRepository;
import com.hotelplatform.hotel.repository.RoomLockRepository;
import com.hotelplatform.hotel.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class HotelIntegrationTest {

    static final String JWT_SECRET = "aG90ZWwtcGxhdGZvcm0tand0LXNlY3JldC1rZXktMDEyMzQ1Njc4OWFiY2RlZg==";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected HotelRepository hotelRepository;
    @Autowired
    protected RoomRepository roomRepository;
    @Autowired
    protected RoomLockRepository roomLockRepository;

    @BeforeEach
    void cleanDatabase() {
        roomLockRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
    }

    protected String adminToken() {
        return token("admin", 1L, "ADMIN");
    }

    protected String userToken() {
        return token("user", 2L, "USER");
    }

    protected String internalToken() {
        return token("booking-service", null, "INTERNAL");
    }

    private String token(String subject, Long uid, String role) {
        return "Bearer " + JwtTokens.issue(JWT_SECRET, "booking-service", subject, uid, role, Duration.ofHours(1));
    }
}
