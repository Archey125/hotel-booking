package com.hotelplatform.booking.config;

import com.hotelplatform.booking.entity.Role;
import com.hotelplatform.booking.entity.UserAccount;
import com.hotelplatform.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    // демо-аккаунты, чтобы после старта можно было сразу логиниться
    @Bean
    @Profile("!test")
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }
            userRepository.save(UserAccount.builder()
                    .username("admin").passwordHash(encoder.encode("admin123")).role(Role.ADMIN).build());
            userRepository.save(UserAccount.builder()
                    .username("user").passwordHash(encoder.encode("user123")).role(Role.USER).build());
            userRepository.save(UserAccount.builder()
                    .username("user2").passwordHash(encoder.encode("user123")).role(Role.USER).build());
            log.info("Предзаполнены пользователи: admin/admin123 (ADMIN), user/user123, user2/user123 (USER)");
        };
    }
}
