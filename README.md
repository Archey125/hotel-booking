# Hotel Booking Platform — микросервисная система бронирования отелей

Учебный проект (итоговая работа): REST API для бронирования отелей на базе **Spring Boot** с
**микросервисной архитектурой**. Реализованы API Gateway, Service Discovery (Eureka),
Hotel Management Service, Booking Service, безопасная JWT-аутентификация с ролями
USER/ADMIN, алгоритм равномерного планирования занятости номеров и распределённая
двухшаговая транзакция (сага) с компенсацией при сбоях.

---

## 1. Архитектура

```
                        ┌──────────────────────┐
                        │   API Gateway :8080  │  Spring Cloud Gateway (WebFlux)
                        │  маршрутизация,      │  CorrelationIdFilter (X-Request-Id)
                        │  lb:// через Eureka  │
                        └─────┬──────────┬─────┘
              /api/bookings/**│          │/api/hotels/** /api/rooms/** /api/statistics/**
              /api/users/**   │          │
                        ┌─────▼─────┐  ┌─▼────────────────────┐
                        │ Booking   │  │ Hotel Management      │
                        │ Service   │  │ Service               │
                        │ :8081     │  │ :8082                 │
                        │ H2 booking│  │ H2 hotel              │
                        └─────┬─────┘  └──────────▲───────────┘
                              │  1) POST /internal/rooms/{id}/confirm-availability
                              │  2) POST /internal/rooms/{id}/release (компенсация)
                              └───────────────────┘
                       retry + exponential backoff + Circuit Breaker (resilience4j)

                        ┌──────────────────────┐
                        │  Eureka Server :8761 │  реестр сервисов (service discovery)
                        └──────────────────────┘
```

* **API Gateway** (`api-gateway`) — единственная входная точка. Маршрутизирует запросы к
  сервисам по имени из Eureka (`lb://booking-service`, `lb://hotel-service`), глобальный
  фильтр `CorrelationIdFilter` создаёт/пробрасывает сквозной `X-Request-Id` и логирует
  каждый запрос. JWT не проверяет — токен пробрасывается в сервисы, каждый из которых
  валидирует его сам (Resource Server).
* **Booking Service** (`booking-service`) — регистрация/авторизация (выпуск JWT, 1 час),
  CRUD бронирований, оркестрация саги, администрирование пользователей.
* **Hotel Management Service** (`hotel-service`) — CRUD отелей и номеров, временные
  блокировки слотов (шаги саги), рекомендации номеров, статистика загруженности.
* **Eureka Server** (`eureka-server`) — service discovery: сервисы регистрируются
  динамически, Gateway и Booking Service находят их по имени (`spring.application.name`).
* Общий модуль **common** — общие контракты (DTO шагов саги), JWT-хелперы, `ErrorDTO`,
  фильтр трассировки, единый обработчик ошибок.

## 2. Технологии

| Компонент | Версия/стек |
|---|---|
| Java | 17+ (собрано и проверено на OpenJDK 21) |
| Spring Boot | 3.5.0 |
| Spring Cloud | 2025.0.0 (BOM) — Eureka, Gateway, Circuit Breaker (resilience4j) |
| Spring Data JPA + H2 | in-memory БД каждого сервиса |
| Spring Security | OAuth2 Resource Server (JWT HS256), @PreAuthorize |
| springdoc-openapi | Swagger UI на каждом сервисе |
| Lombok, MapStruct | сокращение шаблонного кода, маппинг Entity↔DTO |
| Тесты | JUnit 5, MockMvc, WebTestClient/TestRestTemplate, MockRestServiceServer, Mockito |

## 3. Как запустить

Требуется JDK 17+ и Maven 3.9+ (или использовать вписанный в проект `mvnw`, если добавите его).

```bash
# 1) собрать все модули и прогнать тесты
mvn clean package

# 2) запустить в четырёх терминалах
java -jar eureka-server/target/eureka-server-1.0.0.jar
java -jar hotel-service/target/hotel-service-1.0.0.jar
java -jar booking-service/target/booking-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

На Windows проще запустить всё одной командой — `.\demo\demo.bat` (PowerShell или двойной клик):
скрипт проверит порты, запустит каждый сервис в своём окне и дождётся готовности.
Остановка — `.\demo\stop.bat`. Автопроверка API поверх запущенных сервисов — `.\demo\smoke-api.bat`.
Подробный чек-лист проверки: [docs/КАК-ПРОВЕРИТЬ-ПРОГРАММУ.md](docs/КАК-ПРОВЕРИТЬ-ПРОГРАММУ.md).

Порядок важен только логически: сначала Eureka, остальное — в любом порядке; сервисы
перерегистрируются автоматически. Все сервисы регистрируются в Eureka по IP
(`eureka.instance.prefer-ip-address=true`).

| Сервис | Порт | Swagger UI | H2-консоль |
|---|---|---|---|
| API Gateway | 8080 | — | — |
| Eureka Server | 8761 | http://localhost:8761 (панель реестра) | — |
| Booking Service | 8081 | http://localhost:8081/swagger-ui.html | http://localhost:8081/h2-console (JDBC URL `jdbc:h2:mem:bookingdb`) |
| Hotel Service | 8082 | http://localhost:8082/swagger-ui.html | http://localhost:8082/h2-console (JDBC URL `jdbc:h2:mem:hoteldb`) |

**Предзаполненные учётные данные** (см. критерий «предзаполнение данных»):

| Логин | Пароль | Роль | Назначение |
|---|---|---|---|
| `admin` | `admin123` | ADMIN | CRUD отелей/номеров/пользователей, статистика |
| `user` | `user123` | USER | бронирования |
| `user2` | `user123` | USER | проверка «чужих» бронирований (403) |

Hotel Service предзаполнен 3 отелями и 12 номерами с разбросом `times_booked` (0…7),
чтобы на `/api/rooms/recommend` сразу была видна сортировка; один номер `available=false`
(в ремонте).

## 4. Как пользоваться (сквозной сценарий)

Все запросы идут **через Gateway** (порт 8080). Готовая коллекция запросов — в `api.http`
(IntelliJ HTTP Client), автоматический сценарий — `demo/demo.sh`.

```bash
# 1. Регистрация (USER) — токен выдаётся сразу
curl -s -X POST localhost:8080/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"anna","password":"secret123"}'
# => {"accessToken":"eyJ...","tokenType":"Bearer","expiresInMinutes":60,"user":{...}}
TOKEN=eyJ...

# 2. Авторизация (если пользователь уже существует)
curl -s -X POST localhost:8080/api/users/auth \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 3. Смотрим рекомендованные номера (свободные, сортировка по times_booked ASC, id ASC)
curl -s localhost:8080/api/rooms/recommend -H "Authorization: Bearer $TOKEN"

# 4. Создаём бронирование с автоподбором: система возьмёт наименее загруженный номер
curl -s -X POST localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"autoSelect":true,"startDate":"2026-12-01","endDate":"2026-12-03"}'
# => {"id":1,"roomId":...,"status":"CONFIRMED",...}

# 5. Или вручную: autoSelect=false + roomId; повтор с тем же requestId безопасен
curl -s -X POST localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"autoSelect":false,"roomId":3,"startDate":"2026-12-01","endDate":"2026-12-03",
       "requestId":"11111111-1111-1111-1111-111111111111"}'

# 6. История своих бронирований (с пагинацией и фильтрами)
curl -s "localhost:8080/api/bookings?page=0&size=10&status=CONFIRMED" -H "Authorization: Bearer $TOKEN"

# 7. Отмена бронирования — бронь уходит в CANCELLED, блокировка слота снимается (release)
curl -s -X DELETE localhost:8080/api/bookings/1 -H "Authorization: Bearer $TOKEN"

# 8. Админ: статистика загруженности
ADMIN=eyJ...   # токен админа
curl -s localhost:8080/api/statistics/occupancy -H "Authorization: Bearer $ADMIN"
```

Полный список эндпойнтов — ниже, интерактивная документация — в Swagger UI каждого сервиса.

## 5. Эндпойнты

### 5.1 Booking Service (через Gateway: `/api/bookings/**`, `/api/users/**`)

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `POST /api/users/register` | публично | регистрация USER + выдача JWT |
| `POST /api/users/auth` | публично | авторизация + выдача JWT |
| `GET /api/users/me` | USER/ADMIN | текущий пользователь |
| `POST /api/users` | ADMIN | создать пользователя (username, password, role) |
| `PATCH /api/users/{id}` | ADMIN | обновить username/password/role |
| `DELETE /api/users/{id}` | ADMIN | удалить пользователя (и его брони) |
| `GET /api/users` , `GET /api/users/{id}` | ADMIN / сам | список/профиль |
| `POST /api/bookings` | USER/ADMIN | создать бронирование (`autoSelect`, `roomId`, `startDate`, `endDate`, `requestId?`); идемпотентность по `requestId`/заголовку `X-Request-Id`; новое — 201, идемпотентный повтор — 200 |
| `GET /api/bookings` | USER/ADMIN | история своих броней; фильтры `status`, `dateFrom`, `dateTo`; пагинация `page`/`size` |
| `GET /api/bookings/{id}` | владелец/ADMIN | получить бронь |
| `DELETE /api/bookings/{id}` | владелец/ADMIN | отменить бронь (CANCELLED + компенсация release) |
| `GET /api/bookings/all` | ADMIN | все бронирования |
| `GET /api/bookings/recommendations?startDate&endDate` | USER/ADMIN | рекомендованные номера (прокси к Hotel Service) |

### 5.2 Hotel Service (через Gateway: `/api/hotels/**`, `/api/rooms/**`, `/api/statistics/**`)

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `POST /api/hotels` | ADMIN | добавить отель |
| `PATCH /api/hotels/{id}` , `DELETE /api/hotels/{id}` | ADMIN | обновить/удалить отель |
| `GET /api/hotels` , `GET /api/hotels/{id}` | USER/ADMIN | список (фильтры `name`, `city`, пагинация)/детали |
| `POST /api/rooms` , `PATCH /api/rooms/{id}` , `DELETE /api/rooms/{id}` | ADMIN | CRUD номеров |
| `GET /api/rooms` | USER/ADMIN | свободные номера; фильтры `hotelId`, `startDate`+`endDate`, `minPrice`/`maxPrice`, сортировка `sort=pricePerNight,desc` |
| `GET /api/rooms/recommend` | USER/ADMIN (и INTERNAL для автоподбора в саге) | свободные номера, **сортировка по возрастанию `times_booked` (при равенстве — по `id`)** |
| `GET /api/rooms/{id}` | USER/ADMIN | номер по id |
| `GET /api/statistics/occupancy` | ADMIN | статистика: `times_booked`, активные блокировки, заблокированные сутки в периоде |
| `POST /api/rooms/{id}/confirm-availability` | **INTERNAL** | шаг 1 саги: временная блокировка слота на даты (идемпотентно по `requestId`) |
| `POST /api/rooms/{id}/release` | **INTERNAL** | компенсация: снять блокировку (идемпотентно) |
| `POST /internal/rooms/{id}/confirm-availability` , `/internal/rooms/{id}/release` | **INTERNAL** | те же шаги под `/internal/**` — путь, который **не маршрутизируется через Gateway** |

Соответствие эндпойнтам ТЗ: `POST /booking` → `POST /api/bookings`; `POST /user/register` →
`POST /api/users/register`; внутренние шаги саги дополнительно продублированы под
`/internal/**`, чтобы гарантировать, что через Gateway они не публикуются (роль INTERNAL
внешний клиент получить не может — её выдаёт только Booking Service для сам себя).

## 6. Алгоритм планирования занятости номеров (критерий 1)

Hotel Service ведёт по каждому номеру счётчик `times_booked` — сколько раз номер
бронировали (инкремент при успешном `confirm-availability`, декремент при `release`
и при истечении TTL блокировки). Автоподбор номера в Booking Service:

1. Запрашивает у Hotel Service `GET /api/rooms/recommend?startDate&endDate` — свободные
   на даты номера (без живых HELD-блокировок, `available=true`).
2. Берёт **первый** номер списка: сортировка — `times_booked ASC`, при равенстве `id ASC`
   (определено ТЗ и закреплено тестом `RecommendIT`).
3. Так наименее загруженные номера получают приоритет — нагрузка распределяется
   равномерно, номера не «простаивают» и не «перегреваются».

Корректность под нагрузкой: конкурентные попытки занять один номер на пересекающиеся
даты сериализуются пессимистичной блокировкой строки номера
(`SELECT ... FOR UPDATE`, `RoomRepository.findByIdForUpdate`); проигравшие получают
`409 Conflict` (тест `ConcurrentConfirmIT`: 8 параллельных вызовов → ровно 1 успех).

## 7. Распределённая транзакция (сага) и надёжность (критерий 2)

Последовательность локальных транзакций (без XA/2PC):

```
Booking Service                                     Hotel Service
────────────────                                    ─────────────
1. tx: INSERT booking PENDING (commit)
2. POST /internal/rooms/{id}/confirm-availability →
                                                    3. tx: проверка перекрытий + HELD-лок + times_booked+1
   ◄──────────────────── 200 OK (или 409/404/400/5xx/тайм-аут)
4a. tx: PENDING → CONFIRMED                          (успех)
4b. tx: PENDING → CANCELLED  ← КОМПЕНСАЦИЯ          5. POST /internal/rooms/{id}/release
   при 409/404/5xx/тайм-ауте/исчерпании повторов       (снять блокировку, best-effort)
```

Надёжность взаимодействий:

* **Тайм-ауты** — connect 1 c / read 3 c на RestClient (`app.hotel-client.*`).
* **Повторы** — до `max-attempts=3` с экспоненциальным интервалом (200 мс → 400 мс → …,
  cap 2 c, ±20 % jitter). Повторяются только «транспортные» ошибки: сеть/тайм-аут/5xx.
  Бизнес-отказы (409/404/400/401/403) не повторяются — сразу компенсация
  (тест `HotelClientRetryTest`).
* **Circuit Breaker** — resilience4j, инстанс `hotelService` (окно 10 вызовов, порог
  ошибок 50 %, open 10 с): при недоступности Hotel Service запросы мгновенно уходят в
  компенсацию, не копя тайм-ауты.
* **Идемпотентность** — каждый запрос несёт `requestId` (UUID). Booking Service
  дедуплицирует создание брони (уникальный индекс `bookings.request_id`; повтор возвращает
  ту же бронь без повторного confirm, статус ответа 200 вместо 201), Hotel Service
  дедуплицирует блокировки (уникальный индекс `room_locks(room_id, request_id)`;
  повторный confirm возвращает существующий HELD, повторный release — no-op).
  Тесты `IdempotencyIT`, `SagaConfirmReleaseIT`.
* **Сквозная корреляция** — `bookingId`/`requestId` в каждом логе саги; Gateway генерирует
  `X-Request-Id`, сервисы кладут его в MDC (`traceId`) и в ответ; шаблон лога включает
  `[traceId=...]`. Переходы статусов логируются явно: `PENDING -> CONFIRMED`,
  `PENDING -> CANCELLED`, результат компенсации.
* **Свиперы** (самовосстановление):
  * Booking Service: зависшие `PENDING` старше `app.saga.pending-timeout-seconds=120`
    отменяются с компенсацией release (`SweeperService`, тест `SweeperServiceTest`);
  * Hotel Service: HELD-локи с истёкшим TTL (`app.saga.lock-ttl-minutes=15`) переводятся
    в `EXPIRED`, счётчик откатывается (`RoomLockService.expireStaleLocks`).

## 8. Безопасность

* JWT (HS256), срок действия — **1 час**; клеймы: `sub`, `uid`, `role`, `iss`, `exp`.
* Секрет общий для Booking Service (выпуск) и всех проверяющих сервисов; каждый сервис —
  самостоятельный **Resource Server** (`spring-security-oauth2-resource-server`),
  Gateway токен не проверяет, а пробрасывает.
* Роли: `USER` — только личные операции; `ADMIN` — отели/номера/пользователи/статистика;
  `INTERNAL` — сервисная роль для межсервисных вызовов (в БД не существует, выдаётся
  Booking Service только для себя, наружу через Gateway недоступна).
* Разграничение — `@PreAuthorize("hasRole(...)")` на методах + правила в `SecurityFilterChain`.
  Ошибки: 401 (нет/просрочен токен), 403 (нет роли или чужая бронь) — единый `ErrorDTO`.
* Пароли — BCrypt; stateless-сессии; CSRF отключён (REST API, токен-схема).

## 9. Схема баз данных

**Booking Service (H2 `bookingdb`)**

| Таблица | Поля | Индексы/ограничения |
|---|---|---|
| `users` | id, username, password (BCrypt-хэш), role, created_at | `uk_users_username(username)` уникальный, `idx_users_role` |
| `bookings` | id, user_id, room_id, start_date, end_date, status (PENDING/CONFIRMED/CANCELLED), created_at (+ request_id, updated_at) | `uk_bookings_request(request_id)` уникальный, `idx_bookings_user`, `idx_bookings_status`, `idx_bookings_dates` |

`bookings.user_id` — логическая ссылка на `users` (та же БД), `room_id` — ссылка на номер
в Hotel Service без FK (граница микросервиса, ADR-8). Занятость по датам определяется
бронированиями (`start_date`, `end_date`, интервал `[start, end)`), поле `available`
в Hotel Service — только операционная доступность (ремонт/вывод из эксплуатации).

**Hotel Service (H2 `hoteldb`)**

| Таблица | Поля | Индексы/ограничения |
|---|---|---|
| `hotels` | id, name, address, city, created_at | `idx_hotels_city`; связь 1:N с rooms (cascade) |
| `rooms` | id, hotel_id, number, available, times_booked (+ price_per_night, version, created_at) | `idx_rooms_hotel`, `idx_rooms_times_booked`, уникальность номера в отеле на уровне сервиса |
| `room_locks` | id, room_id, request_id, booking_id, start_date, end_date, status (HELD/RELEASED/EXPIRED), created_at, expires_at | `uk_locks_room_request(room_id, request_id)` уникальный, `idx_locks_room_dates`, `idx_locks_expires` |

## 10. Тестирование

Запуск всех тестов: `mvn test` (или `mvn clean package`). Отдельный модуль:
`mvn test -pl hotel-service`.

**hotel-service**

| Тест | Что проверяет |
|---|---|
| `HotelRoomCrudIT` | CRUD отелей/номеров: 201/200/204; 401 без токена, 403 у USER, 404/409 |
| `SagaConfirmReleaseIT` | confirm → HELD + times_booked+1; идемпотентный повтор; 409 на пересечение; «стыковка» дат `[start,end)`; release и повторный release — no-op; 403/401 для не-INTERNAL; 400/404; свипер EXPIRED |
| `ConcurrentConfirmIT` | 8 параллельных confirm на один номер: ровно 1 успех, 7×409, ровно 1 блокировка, times_booked=1 |
| `RecommendIT` | сортировка times_booked ASC, тай-брейк по id; available=false исключён; фильтр по датам; фильтры/сортировка списка номеров |
| `StatisticsIT` | статистика для ADMIN (сортировка по times_booked desc, активные блокировки, сутки в периоде); 403/401 |
| `HotelServiceApplicationTests` | подъем контекста |

**booking-service**

| Тест | Что проверяет |
|---|---|
| `SagaSuccessIT` | автоподбор + confirm → CONFIRMED, проверяется payload вызова Hotel Service; чтение брони, история |
| `SagaFailureIT` | 409 от Hotel → бронь CANCELLED + release, наружу 409; недоступность Hotel (после повторов) → 502 + компенсация; нет номеров → 409 без создания брони |
| `IdempotencyIT` | повтор с тем же requestId → та же бронь (200), без дубля и без второго confirm; чужой requestId / другой payload → 409 |
| `BookingAccessIT` | чужие брони недоступны USER (403), ADMIN видит/отменяет любые; `/all` только ADMIN; 401 без токена |
| `BookingValidationIT` | валидация дат: end≤start, прошлые даты, manual без roomId, пустые поля — 400; аноним 401 |
| `PaginationIT` | пагинация (page/size/totalPages) и фильтры (status, период) |
| `SweeperServiceTest` | свипер отменяет старый PENDING с компенсацией, свежий не трогает |
| `HotelClientRetryTest` | retry: 2×500 → успех на 3-й попытке; 409 без повторов; исчерпание повторов → IntegrationException |
| `BookingServiceApplicationTests` | подъем контекста |

**api-gateway / eureka-server**: контекст и маршруты (`routesAreConfigured`), юнит-тест
`CorrelationIdFilter` (генерация/проброс `X-Request-Id`).

Смок-проверка всей системы вживую: `bash demo/demo.sh` (поднимает сервисы, прогоняет
сквозной сценарий: регистрация → рекомендация → бронь → идемпотентный повтор → конфликт
дат → отмена → статистика).
