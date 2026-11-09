package com.hotelplatform.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

// проставляем X-Request-Id каждому запросу, если его не было, и пишем его в лог
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        final String id = traceId;
        log.info("[gateway] -> {} {} traceId={}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), id);
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set(HEADER, id)))
                .build();
        return chain.filter(mutated)
                .then(Mono.fromRunnable(() -> {
                    var response = exchange.getResponse();
                    log.info("[gateway] <- {} {} status={} traceId={}",
                            exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                            response.getStatusCode(), id);
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
