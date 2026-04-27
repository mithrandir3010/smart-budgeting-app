package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AuthResponse;
import com.mali.smartbudget.dto.AuthTokenResult;
import com.mali.smartbudget.dto.LoginRequest;
import com.mali.smartbudget.dto.RegisterRequest;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ── UserDetailsService ─────────────────────────────────────────────────────

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Kullanıcı bulunamadı: " + username));
    }

    // ── Kayıt ─────────────────────────────────────────────────────────────────

    public AuthTokenResult register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                    "Bu kullanıcı adı zaten alınmış: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Bu e-posta zaten kayıtlı: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role("ROLE_USER")
                .build();

        userRepository.save(user);
        log.info("Yeni kullanıcı kaydedildi: {}", user.getUsername());

        String token = jwtService.generateToken(user);
        return new AuthTokenResult(token, new AuthResponse(user.getUsername(), user.getEmail(), user.getFullName()));
    }

    // ── Giriş ─────────────────────────────────────────────────────────────────

    public AuthTokenResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Kullanıcı adı veya şifre hatalı."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Kullanıcı adı veya şifre hatalı.");
        }

        String token = jwtService.generateToken(user);
        log.info("Kullanıcı giriş yaptı: {}", user.getUsername());
        return new AuthTokenResult(token, new AuthResponse(user.getUsername(), user.getEmail(), user.getFullName()));
    }
}
