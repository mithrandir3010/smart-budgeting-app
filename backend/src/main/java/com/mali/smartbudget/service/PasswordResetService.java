package com.mali.smartbudget.service;

import com.mali.smartbudget.model.PasswordResetToken;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.PasswordResetTokenRepository;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void sendResetEmail(String email) {
        // E-posta bulunamazsa hata vermiyoruz — e-posta numaralandırma saldırısını önler
        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            tokenRepository.deleteByUser(user);
            String tokenValue = UUID.randomUUID().toString();
            tokenRepository.save(PasswordResetToken.builder()
                    .token(tokenValue)
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build());
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), tokenValue);
            log.info("Şifre sıfırlama e-postası gönderildi: username={}", user.getUsername());
        });
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Yeni şifre ve şifre tekrarı eşleşmiyor.");
        }

        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Geçersiz veya süresi dolmuş şifre sıfırlama linki."));

        if (token.isUsed()) {
            throw new IllegalArgumentException("Bu sıfırlama linki zaten kullanılmış.");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Şifre sıfırlama linkinin süresi dolmuş. Lütfen tekrar talep edin.");
        }

        User user = token.getUser();

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("Yeni şifre mevcut şifreyle aynı olamaz.");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        log.info("Şifre sıfırlandı: username={}", user.getUsername());
    }
}
