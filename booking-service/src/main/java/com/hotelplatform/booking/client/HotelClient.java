package com.hotelplatform.booking.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.hotelplatform.booking.config.HotelClientProperties;
import com.hotelplatform.booking.dto.RoomRecommendationDto;
import com.hotelplatform.booking.security.JwtService;
import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ConfirmAvailabilityResponse;
import com.hotelplatform.common.api.ReleaseRequest;
import com.hotelplatform.common.api.ReleaseResponse;
import com.hotelplatform.common.exceptions.ApiException;
import com.hotelplatform.common.exceptions.BadRequestException;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.IntegrationException;
import com.hotelplatform.common.exceptions.NotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

// http-клиент к hotel-service: инстанс из eureka, ретраи с паузами, circuit breaker
@Component
@Slf4j
public class HotelClient {

    public static final String CB_INSTANCE = "hotelService";
    public static final String SERVICE_ID = "hotel-service";

    private final RestClient restClient;
    private final JwtService jwtService;
    private final HotelClientProperties props;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final LoadBalancerClient loadBalancerClient;
    private final Random random = new Random();

    @Autowired
    public HotelClient(RestClient hotelRestClient, JwtService jwtService, HotelClientProperties props,
                       ObjectProvider<CircuitBreakerFactory> circuitBreakerFactory,
                       LoadBalancerClient loadBalancerClient) {
        this(hotelRestClient, jwtService, props, circuitBreakerFactory.getIfAvailable(), loadBalancerClient);
    }

    public HotelClient(RestClient restClient, JwtService jwtService, HotelClientProperties props,
                       CircuitBreakerFactory circuitBreakerFactory, LoadBalancerClient loadBalancerClient) {
        this.restClient = restClient;
        this.jwtService = jwtService;
        this.props = props;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.loadBalancerClient = loadBalancerClient;
    }

    public List<RoomRecommendationDto> recommend(LocalDate startDate, LocalDate endDate) {
        StringBuilder query = new StringBuilder();
        if (startDate != null) {
            query.append("?startDate=").append(startDate);
            if (endDate != null) {
                query.append("&endDate=").append(endDate);
            }
        }
        return withRetry("recommend", () -> restClient.get()
                .uri(resolve("/api/rooms/recommend" + query))
                .header(HttpHeaders.AUTHORIZATION, internalBearerToken())
                .retrieve()
                .body(new ParameterizedTypeReference<List<RoomRecommendationDto>>() {
                }));
    }

    public ConfirmAvailabilityResponse confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        return withRetry("confirm-availability", () -> restClient.post()
                .uri(resolve("/internal/rooms/" + roomId + "/confirm-availability"))
                .header(HttpHeaders.AUTHORIZATION, internalBearerToken())
                .header("X-Request-Id", request.requestId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ConfirmAvailabilityResponse.class));
    }

    public ReleaseResponse release(Long roomId, ReleaseRequest request) {
        return withRetry("release", () -> restClient.post()
                .uri(resolve("/internal/rooms/" + roomId + "/release"))
                .header(HttpHeaders.AUTHORIZATION, internalBearerToken())
                .header("X-Request-Id", request.requestId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ReleaseResponse.class));
    }

    private java.net.URI resolve(String path) {
        // живой инстанс из реестра берём на каждую попытку, а не один раз на старте
        if (loadBalancerClient == null) {
            return java.net.URI.create(props.baseUrl() + path);
        }
        ServiceInstance instance = loadBalancerClient.choose(SERVICE_ID);
        if (instance == null) {
            log.warn("[saga] в реестре нет инстансов {}", SERVICE_ID);
            throw new IntegrationException("Hotel Service: нет доступных инстансов в реестре", null, true);
        }
        return loadBalancerClient.reconstructURI(instance, java.net.URI.create("http://" + SERVICE_ID))
                .resolve(path);
    }

    // --- механизм повторов ---

    private <T> T withRetry(String operation, Supplier<T> call) {
        int attempts = props.maxAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return executeThroughCircuitBreaker(operation, call);
            } catch (IntegrationException e) {
                if (!e.isRetryable() || attempt == attempts) {
                    log.warn("[saga] вызов '{}' исчерпал попытки ({}/{}): {}",
                            operation, attempt, attempts, e.getMessage());
                    throw e;
                }
                long backoff = backoffMs(attempt);
                log.warn("[saga] вызов '{}' попытка {}/{} не удалась ({}), повтор через {} мс",
                        operation, attempt, attempts, e.getMessage(), backoff);
                sleep(backoff);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private <T> T executeThroughCircuitBreaker(String operation, Supplier<T> call) {
        if (circuitBreakerFactory == null) {
            return executeOnce(operation, call);
        }
        try {
            return circuitBreakerFactory.create(CB_INSTANCE).run(() -> executeOnce(operation, call));
        } catch (CallNotPermittedException e) {
            // цепь открыта - hotel явно лежит, повторять смысла нет, сразу компенсация
            throw new IntegrationException("CircuitBreaker '" + CB_INSTANCE + "' открыт: Hotel Service недоступен",
                    e, false);
        } catch (org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException e) {
            // без fallback-функции CB заворачивает наши исключения - разворачиваем обратно,
            // чтобы retry-логика видела исходный IntegrationException
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private <T> T executeOnce(String operation, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            throw classify(operation, e);
        } catch (ResourceAccessException e) {
            log.warn("[saga] '{}' тайм-аут/сеть: {}", operation, e.getMessage());
            throw new IntegrationException("Hotel Service не ответил (тайм-аут/сеть)", e, true);
        }
    }

    private RuntimeException classify(String operation, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String message = extractErrorMessage(e);
        if (status == 409) {
            return new ConflictException(message);
        }
        if (status == 404) {
            return new NotFoundException(message);
        }
        if (status == 400) {
            return new BadRequestException(message);
        }
        if (status == 401 || status == 403) {
            return new IntegrationException(
                    "Hotel Service отклонил доступ при '%s': HTTP %d (%s)".formatted(operation, status, message),
                    e, false);
        }
        return new IntegrationException(
                "Hotel Service вернул ошибку при '%s': HTTP %d (%s)".formatted(operation, status, message),
                e, true);
    }

    private String extractErrorMessage(RestClientResponseException e) {
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(e.getResponseBodyAsString());
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // тело не JSON — используем статусную строку
        }
        return e.getStatusText();
    }

    private long backoffMs(int attempt) {
        double base = props.backoffInitialMs() * Math.pow(props.backoffMultiplier(), attempt - 1);
        long capped = Math.min((long) base, props.backoffMaxMs());
        long jitter = (long) (capped * 0.2 * (random.nextDouble() * 2 - 1));
        return Math.max(50, capped + jitter);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IntegrationException("Ожидание повтора прервано", ie, false);
        }
    }

    private String internalBearerToken() {
        return "Bearer " + jwtService.issueInternalToken();
    }
}
