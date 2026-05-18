package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AuthResponse;
import com.mali.smartbudget.dto.AuthTokenResult;
import com.mali.smartbudget.dto.LoginRequest;
import com.mali.smartbudget.dto.RegisterRequest;
import com.mali.smartbudget.model.RefreshToken;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final AuditService auditService;

    @Value("${app.security.email-verification-skip:false}")
    private boolean emailVerificationSkip;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Kullanıcı bulunamadı: " + username));
    }

    private static final java.util.Set<String> DISPOSABLE_DOMAINS = java.util.Set.of(
            "mailinator.com", "tempmail.com", "10minutemail.com", "guerrillamail.com",
            "throwam.com", "yopmail.com", "trashmail.com", "fakeinbox.com",
            "maildrop.cc", "dispostable.com", "sharklasers.com", "guerrillamailblock.com",
            "grr.la", "guerrillamail.info", "guerrillamail.biz", "guerrillamail.de",
            "guerrillamail.net", "guerrillamail.org", "spam4.me", "tempr.email",
            "discard.email", "spamgourmet.com", "spamgourmet.net", "spamgourmet.org",
            "spamcorner.com", "getairmail.com", "filzmail.com",
            "tempinbox.com", "mailnesia.com", "mailnull.com"
    );

    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                    "Bu kullanıcı adı zaten alınmış: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Bu e-posta zaten kayıtlı: " + request.email());
        }
        String emailDomain = request.email().substring(request.email().lastIndexOf('@') + 1).toLowerCase();
        if (DISPOSABLE_DOMAINS.contains(emailDomain)) {
            auditService.disposableEmailBlocked(request.email(), "N/A");
            throw new IllegalArgumentException(
                    "Geçici e-posta adresleri ile kayıt olunamamaktadır.");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role("ROLE_USER")
                .emailVerified(emailVerificationSkip)
                .build();

        userRepository.save(user);

        if (!emailVerificationSkip) {
            String token = emailVerificationService.createToken(user);
            emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
        } else {
            log.warn("E-posta doğrulama atlandı (SES sandbox modu): {}", user.getEmail());
        }
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    public AuthTokenResult login(LoginRequest request, String ip) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Kullanıcı adı veya şifre hatalı."));

        if (!user.isAccountNonLocked()) {
            auditService.accountLocked(user.getUsername(), ip);
            throw new LockedException("Hesabınız geçici olarak kilitlendi. Lütfen 15 dakika sonra tekrar deneyin.");
        }

        if (!user.isActive()) {
            throw new DisabledException("Hesabınız yönetici tarafından devre dışı bırakıldı.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60L));
                auditService.accountLocked(user.getUsername(), ip);
            } else {
                auditService.loginFailure(user.getUsername(), ip, attempts);
            }
            userRepository.save(user);
            throw new BadCredentialsException("Kullanıcı adı veya şifre hatalı.");
        }

        if (!user.isEmailVerified()) {
            throw new DisabledException("E-posta adresinizi doğrulamanız gerekiyor.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLoginCount(user.getLoginCount() + 1);
        userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        auditService.loginSuccess(user.getUsername(), ip);
        return new AuthTokenResult(accessToken, refreshToken.getToken(),
                new AuthResponse(user.getUsername(), user.getEmail(), user.getFullName(), user.getRole()));
    }
}
