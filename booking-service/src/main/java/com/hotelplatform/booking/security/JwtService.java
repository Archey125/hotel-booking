package com.hotelplatform.booking.security;

import com.hotelplatform.booking.config.JwtProperties;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.common.security.JwtTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

// выпуск токенов: обычный при входе/регистрации, сервисный для вызовов hotel-service
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;

    public String issueForUser(UserAccount user) {
        return JwtTokens.issue(properties.secret(), properties.issuer(),
                user.getUsername(), user.getId(), user.getRole().name(),
                Duration.ofMinutes(properties.ttlMinutes()));
    }

    public String issueInternalToken() {
        return JwtTokens.issueInternal(properties.secret(), properties.issuer());
    }

    public long ttlMinutes() {
        return properties.ttlMinutes();
    }
}
