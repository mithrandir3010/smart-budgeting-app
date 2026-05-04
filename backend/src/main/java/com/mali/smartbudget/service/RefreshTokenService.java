package com.mali.smartbudget.service;

import com.mali.smartbudget.exception.InvalidRefreshTokenException;
import com.mali.smartbudget.model.RefreshToken;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);
        // Hibernate action queue'da INSERT, DELETE'den önce çalışır;
        // flush() ile DELETE'i DB'ye gönderdikten sonra INSERT yaparız.
        refreshTokenRepository.flush();

        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(Instant.now().plusMillis(refreshExpiration))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken validateAndRotate(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new InvalidRefreshTokenException("Geçersiz refresh token."));

        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token iptal edilmiş.");
        }

        if (existing.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(existing);
            throw new InvalidRefreshTokenException("Refresh token süresi dolmuş. Lütfen tekrar giriş yapın.");
        }

        // Token rotation: revoke old, create new
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return createRefreshToken(existing.getUser());
    }

    @Transactional
    public void revokeByToken(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }
}
