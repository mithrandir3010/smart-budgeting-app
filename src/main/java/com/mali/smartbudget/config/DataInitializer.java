package com.mali.smartbudget.config;

import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_USERNAME = "mali";
    private static final String DEFAULT_EMAIL    = "test@mali.com";
    private static final String DEFAULT_PASSWORD = "test1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(DEFAULT_USERNAME)) {
            log.info("DataInitializer: '{}' zaten mevcut, atlanıyor.", DEFAULT_USERNAME);
            return;
        }

        User testUser = User.builder()
                .username(DEFAULT_USERNAME)
                .email(DEFAULT_EMAIL)
                .fullName("Mali Test Kullanıcısı")
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .role("ROLE_USER")
                .build();

        userRepository.save(testUser);
        log.info("DataInitializer: Varsayılan kullanıcı oluşturuldu → username='{}', email='{}'",
                DEFAULT_USERNAME, DEFAULT_EMAIL);
        log.info("DataInitializer: Giriş için → username: {}, password: {}", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
