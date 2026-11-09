package com.hotelplatform.booking.service;

import com.hotelplatform.booking.client.HotelClient;
import com.hotelplatform.booking.config.BookingSagaProperties;
import com.hotelplatform.booking.dto.BookingDto;
import com.hotelplatform.booking.dto.CreateBookingRequest;
import com.hotelplatform.booking.dto.RoomRecommendationDto;
import com.hotelplatform.booking.entity.Booking;
import com.hotelplatform.booking.entity.BookingStatus;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.booking.mapper.BookingMapper;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.booking.repository.UserRepository;
import com.hotelplatform.common.api.ConfirmAvailabilityRequest;
import com.hotelplatform.common.api.ReleaseRequest;
import com.hotelplatform.common.exceptions.ApiException;
import com.hotelplatform.common.exceptions.BadRequestException;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.IntegrationException;
import com.hotelplatform.common.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// двухшаговая сага: бронь в своей БД (PENDING) -> confirm в hotel-service -> CONFIRMED
// если confirm не вышел - отмена (CANCELLED) + release, чтобы снять блокировку слота
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSagaService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingStateService stateService;
    private final HotelClient hotelClient;
    private final BookingMapper bookingMapper;
    private final BookingSagaProperties sagaProperties;

    public record CreateResult(BookingDto booking, boolean newlyCreated) {
    }

    public CreateResult create(Long userId, CreateBookingRequest request, UUID headerRequestId) {
        validateDates(request);
        // requestId из тела важнее заголовка: gateway всегда ставит свой X-Request-Id
        UUID requestId = request.requestId() != null
                ? request.requestId()
                : headerRequestId != null ? headerRequestId : UUID.randomUUID();

        Booking existing = bookingRepository.findByRequestId(requestId).orElse(null);
        if (existing != null) {
            return new CreateResult(returnExisting(existing, userId, request), false);
        }

        Long roomId = resolveRoom(request);

        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь", userId));
        Booking pending = stateService.markPending(user, roomId, request, requestId);

        try {
            ConfirmAvailabilityRequest confirmRequest = new ConfirmAvailabilityRequest(
                    requestId, pending.getId(), request.startDate(), request.endDate());
            log.info("[saga] шаг 2: запрос подтверждения доступности roomId={} bookingId={} requestId={}",
                    roomId, pending.getId(), requestId);
            hotelClient.confirmAvailability(roomId, confirmRequest);
            Booking confirmed = stateService.markConfirmed(pending.getId());
            log.info("[saga] бронирование bookingId={} создано: CONFIRMED, requestId={}", confirmed.getId(), requestId);
            return new CreateResult(bookingMapper.toDto(confirmed), true);
        } catch (RuntimeException e) {
            String reason = e instanceof ApiException ae
                    ? "сбой подтверждения: " + ae.getMessage()
                    : "неожиданная ошибка: " + e.getMessage();
            log.warn("[saga] подтверждение не удалось для bookingId={} requestId={}: {} — запускаю компенсацию",
                    pending.getId(), requestId, e.getMessage());
            stateService.markCancelled(pending.getId(), reason);
            releaseQuietly(roomId, requestId, pending.getId());
            if (e instanceof ApiException apiException) {
                throw apiException; // 409/404/502 наружу как есть
            }
            throw new IntegrationException("Hotel Service недоступен; бронирование отменено (компенсация выполнена)",
                    e, false);
        }
    }

    public BookingDto cancel(Long callerUserId, Long bookingId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронирование", bookingId));
        if (!isAdmin && !booking.getUserId().equals(callerUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Можно отменять только свои бронирования");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("[saga] повторная отмена bookingId={} — идемпотентный no-op", bookingId);
            return bookingMapper.toDto(booking);
        }
        stateService.markCancelled(bookingId, isAdmin ? "отменено администратором" : "отменено пользователем");
        releaseQuietly(booking.getRoomId(), booking.getRequestId(), booking.getId());
        return bookingMapper.toDto(bookingRepository.findById(bookingId).orElseThrow());
    }

    private BookingDto returnExisting(Booking existing, Long userId, CreateBookingRequest request) {
        if (!existing.getUserId().equals(userId)) {
            throw new ConflictException("requestId уже использован другим пользователем");
        }
        boolean sameDates = existing.getStartDate().equals(request.startDate())
                && existing.getEndDate().equals(request.endDate());
        boolean samePayload = Boolean.TRUE.equals(request.autoSelect())
                ? sameDates // конкретный номер при автоподборе определяется на шаге саги
                : sameDates && request.roomId() != null && request.roomId().equals(existing.getRoomId());
        if (!samePayload) {
            throw new ConflictException("requestId уже использован с другим содержимым запроса");
        }
        log.info("[saga] идемпотентный повтор создания: bookingId={} requestId={} возвращён без изменений",
                existing.getId(), existing.getRequestId());
        return bookingMapper.toDto(existing);
    }

    private Long resolveRoom(CreateBookingRequest request) {
        if (Boolean.TRUE.equals(request.autoSelect())) {
            List<RoomRecommendationDto> rooms = hotelClient.recommend(request.startDate(), request.endDate());
            if (rooms.isEmpty()) {
                throw new ConflictException("Нет свободных номеров на выбранные даты");
            }
            RoomRecommendationDto chosen = rooms.get(0);
            log.info("[saga] автоподбор: выбран roomId={} (times_booked={}) из {} кандидатов",
                    chosen.id(), chosen.timesBooked(), rooms.size());
            return chosen.id();
        }
        if (request.roomId() == null) {
            throw new BadRequestException("При autoSelect=false обязателен roomId");
        }
        return request.roomId();
    }

    private void validateDates(CreateBookingRequest request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        if (start == null || end == null) {
            throw new BadRequestException("startDate и endDate обязательны");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("startDate должна быть раньше endDate (даты [start, end))");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new BadRequestException("startDate не может быть в прошлом");
        }
        if (start.plusDays(365).isBefore(end)) {
            throw new BadRequestException("Максимальный срок бронирования — 365 дней");
        }
    }

    private void releaseQuietly(Long roomId, UUID requestId, Long bookingId) {
        // best-effort: если release не вышел, слот всё равно освободит свипер hotel по TTL
        try {
            var response = hotelClient.release(roomId, new ReleaseRequest(requestId, bookingId));
            log.info("[saga] компенсация release выполнена: roomId={} requestId={} -> {}",
                    roomId, requestId, response.status());
        } catch (RuntimeException e) {
            log.error("[saga] компенсация release НЕ удалась: roomId={} requestId={} bookingId={}; " +
                            "слот освободит свипер Hotel Service по TTL. Причина: {}",
                    roomId, requestId, bookingId, e.getMessage());
        }
    }
}
