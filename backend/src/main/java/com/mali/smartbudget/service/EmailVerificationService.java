package com.mali.smartbudget.service;

import com.mali.smartbudget.model.EmailVerificationToken;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.EmailVerificationTokenRepository;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public String createToken(User user) {
        tokenRepository.deleteByUser(user);
        String tokenValue = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());
        return tokenValue;
    }

    @Transactional
    public void verifyToken(String tokenValue) {
        EmailVerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Geçersiz veya süresi dolmuş doğrulama linki."));

        if (token.isUsed()) {
            throw new IllegalArgumentException("Bu doğrulama linki zaten kullanılmış.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Doğrulama linkinin süresi dolmuş. Lütfen tekrar kayıt olun.");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("E-posta doğrulandı: username={}", user.getUsername());
    }
}
