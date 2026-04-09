package com.mali.smartbudget.config;

import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String TEST_EMAIL = "test@mali.com";

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(TEST_EMAIL)) {
            log.info("DataInitializer: '{}' zaten mevcut, atlanıyor.", TEST_EMAIL);
            return;
        }

        User testUser = User.builder()
                .email(TEST_EMAIL)
                .fullName("Mali Test Kullanıcısı")
                // TODO: Spring Security eklenince BCryptPasswordEncoder ile hash'lenecek
                .password("test1234")
                .build();

        userRepository.save(testUser);
        log.info("DataInitializer: Test kullanıcısı oluşturuldu → {}", TEST_EMAIL);
    }
}
