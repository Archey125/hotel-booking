package com.hotelplatform.booking.service;

import com.hotelplatform.booking.dto.AdminCreateUserRequest;
import com.hotelplatform.booking.dto.AuthResponse;
import com.hotelplatform.booking.dto.AuthRequest;
import com.hotelplatform.booking.dto.RegisterRequest;
import com.hotelplatform.booking.dto.UpdateUserRequest;
import com.hotelplatform.booking.dto.UserDto;
import com.hotelplatform.booking.entity.Role;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.booking.mapper.UserMapper;
import com.hotelplatform.booking.repository.BookingRepository;
import com.hotelplatform.booking.repository.UserRepository;
import com.hotelplatform.booking.security.JwtService;
import com.hotelplatform.common.exceptions.ConflictException;
import com.hotelplatform.common.exceptions.NotFoundException;
import com.hotelplatform.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// пользователи: регистрация, вход, админские CRUD
// пароль в открытом виде не храним - только bcrypt-хэш
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        ensureUsernameFree(request.username());
        UserAccount user = UserAccount.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();
        user = userRepository.save(user);
        log.info("Зарегистрирован пользователь id={} username={}", user.getId(), user.getUsername());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        UserAccount user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new BadCredentialsException("Неверное имя пользователя или пароль"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Неверное имя пользователя или пароль");
        }
        log.info("Успешный вход пользователя id={}", user.getId());
        return toAuthResponse(user);
    }

    @Transactional
    public UserDto adminCreate(AdminCreateUserRequest request) {
        ensureUsernameFree(request.username());
        UserAccount user = UserAccount.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        user = userRepository.save(user);
        log.info("Администратор создал пользователя id={} role={}", user.getId(), user.getRole());
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto adminUpdate(Long id, UpdateUserRequest request) {
        UserAccount user = getEntity(id);
        if (request.username() != null && !request.username().equalsIgnoreCase(user.getUsername())) {
            ensureUsernameFree(request.username());
            user.setUsername(request.username());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        log.info("Администратор обновил пользователя id={}", id);
        return userMapper.toDto(user);
    }

    @Transactional
    public void adminDelete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("Пользователь", id);
        }
        bookingRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        log.info("Администратор удалил пользователя id={} (и его бронирования)", id);
    }

    @Transactional(readOnly = true)
    public UserDto getById(Long id) {
        return userMapper.toDto(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Page<UserDto> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDto);
    }

    private void ensureUsernameFree(String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Имя пользователя '%s' уже занято".formatted(username));
        }
    }

    private UserAccount getEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь", id));
    }

    private AuthResponse toAuthResponse(UserAccount user) {
        return new AuthResponse(jwtService.issueForUser(user), "Bearer", jwtService.ttlMinutes(),
                userMapper.toDto(user));
    }
}
