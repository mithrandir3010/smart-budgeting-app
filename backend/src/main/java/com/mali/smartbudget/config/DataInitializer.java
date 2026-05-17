package com.mali.smartbudget.config;

import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL    = "admin@smartbudgetr.com";

    @Value("${app.admin.password:SmartBudgetAdmin2024}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
            userRepository.save(User.builder()
                    .username(ADMIN_USERNAME)
                    .email(ADMIN_EMAIL)
                    .fullName("Admin")
                    .password(passwordEncoder.encode(adminPassword))
                    .role("ROLE_ADMIN")
                    .emailVerified(true)
                    .build());
            log.info("DataInitializer: Admin kullanıcısı oluşturuldu → username='{}'", ADMIN_USERNAME);
        }
    }
}
