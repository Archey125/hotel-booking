package com.hotelplatform.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelplatform.booking.entity.Role;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.booking.repository.UserRepository;
import com.hotelplatform.common.security.JwtTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BookingIntegrationTest {

    static final String JWT_SECRET = "aG90ZWwtcGxhdGZvcm0tand0LXNlY3JldC1rZXktMDEyMzQ1Njc4OWFiY2RlZg==";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected BookingRepository bookingRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected UserAccount admin;
    protected String adminToken;

    @BeforeEach
    void seedAdmin() {
        // H2 с DB_CLOSE_DELAY=-1 живёт всю JVM и делится между контекстами
        bookingRepository.deleteAll();
        userRepository.deleteAll();
        admin = userRepository.save(UserAccount.builder()
                .username("test-admin-" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .build());
        adminToken = bearer("test-admin", admin.getId(), "ADMIN");
    }

    protected String bearer(String username, Long userId, String role) {
        return "Bearer " + JwtTokens.issue(JWT_SECRET, "booking-service", username, userId, role, Duration.ofHours(1));
    }

    protected String registerAndToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterPayload(username, "user123"));
        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/users/register").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    protected Long userIdByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
    }

    public record RegisterPayload(String username, String password) {
    }
}
