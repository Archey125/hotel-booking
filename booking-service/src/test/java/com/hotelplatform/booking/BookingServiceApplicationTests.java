package com.hotelplatform.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "eureka.client.enabled=false")
@ActiveProfiles("test")
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
        // SecurityConfig, JPA, сваггер, resilience4j — контекст собирается
    }
}
