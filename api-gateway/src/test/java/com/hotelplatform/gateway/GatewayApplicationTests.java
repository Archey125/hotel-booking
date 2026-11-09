package com.hotelplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
        // Контекст WebFlux-шлюза стартует без Eureka (клиент отключён в тесте)
    }

    @Test
    void routesAreConfigured() {
        List<String> ids = routeLocator.getRoutes().map(r -> r.getId()).collectList().block();
        assertThat(ids).contains("booking-service", "hotel-service");
    }
}
