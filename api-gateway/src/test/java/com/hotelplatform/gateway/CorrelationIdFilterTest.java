package com.hotelplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    private ServerWebExchange capturedExchange(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.empty();
        });
        filter.filter(exchange, chain).block();
        return captured.get();
    }

    @Test
    void generatesTraceIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/bookings").build());

        ServerWebExchange passed = capturedExchange(exchange);

        assertThat(passed.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void propagatesIncomingTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/hotels")
                        .header(CorrelationIdFilter.HEADER, "trace-123").build());

        ServerWebExchange passed = capturedExchange(exchange);

        assertThat(passed.getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER))
                .isEqualTo("trace-123");
    }
}
