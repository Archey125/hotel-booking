package com.hotelplatform.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HotelServiceApplicationTests {

    @Test
    void contextLoads() {
        // SecurityConfig, JPA, сваггер, свипер — контекст собирается
    }
}
