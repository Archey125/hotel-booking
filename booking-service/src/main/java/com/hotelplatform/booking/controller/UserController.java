package com.hotelplatform.booking.controller;

import com.hotelplatform.booking.dto.AdminCreateUserRequest;
import com.hotelplatform.booking.dto.AuthRequest;
import com.hotelplatform.booking.dto.AuthResponse;
import com.hotelplatform.booking.dto.RegisterRequest;
import com.hotelplatform.booking.dto.UpdateUserRequest;
import com.hotelplatform.booking.dto.UserDto;
import com.hotelplatform.booking.service.UserService;
import com.hotelplatform.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Регистрация, авторизация, администрирование пользователей")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Регистрация пользователя: создаёт USER и возвращает JWT")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @Operation(summary = "Авторизация: проверка пары логин/пароль, новый JWT")
    @PostMapping("/auth")
    public AuthResponse authenticate(@Valid @RequestBody AuthRequest request) {
        return userService.authenticate(request);
    }

    @Operation(summary = "Создать пользователя (ADMIN): username, password, role")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserDto> create(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.adminCreate(request));
    }

    @Operation(summary = "Обновить пользователя (ADMIN): username/password/role")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.adminUpdate(id, request);
    }

    @Operation(summary = "Удалить пользователя (ADMIN), вместе с его бронированиями")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Список пользователей (ADMIN), пагинация")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    public PageResponse<UserDto> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(userService.list(PageRequest.of(page, size, Sort.by("id"))));
    }

    @Operation(summary = "Пользователь по id (ADMIN или сам пользователь)")
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public UserDto get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long callerId = Long.valueOf(jwt.getClaimAsString(JwtClaims.UID));
        boolean isAdmin = "ADMIN".equals(jwt.getClaimAsString(JwtClaims.ROLE));
        if (!isAdmin && !callerId.equals(id)) {
            throw new org.springframework.security.access.AccessDeniedException("Доступна только своя учётная запись");
        }
        return userService.getById(id);
    }

    @Operation(summary = "Текущий пользователь по своему JWT")
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getById(Long.valueOf(jwt.getClaimAsString(JwtClaims.UID)));
    }

    private static final class JwtClaims {
        static final String UID = "uid";
        static final String ROLE = "role";
    }
}
