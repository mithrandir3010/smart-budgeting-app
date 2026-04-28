package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AuthResponse;
import com.mali.smartbudget.dto.AuthTokenResult;
import com.mali.smartbudget.dto.LoginRequest;
import com.mali.smartbudget.dto.RegisterRequest;
import com.mali.smartbudget.model.RefreshToken;
import com.mali.smartbudget.service.AuthService;
import com.mali.smartbudget.service.RefreshTokenService;
import com.mali.smartbudget.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    @Value("${jwt.expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Kayıt isteği: username={}", request.username());
        AuthTokenResult result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE,
                        buildAccessCookie(result.accessToken()).toString(),
                        buildRefreshCookie(result.refreshToken()).toString())
                .body(result.userInfo());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Giriş isteği: username={}", request.username());
        AuthTokenResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildAccessCookie(result.accessToken()).toString(),
                        buildRefreshCookie(result.refreshToken()).toString())
                .body(result.userInfo());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String tokenValue = extractCookieValue(request, "refresh_token")
                .orElseThrow(() -> new com.mali.smartbudget.exception.InvalidRefreshTokenException(
                        "Refresh token bulunamadı."));

        RefreshToken rotated = refreshTokenService.validateAndRotate(tokenValue);
        String newAccessToken = jwtService.generateToken(rotated.getUser());

        log.info("Token yenilendi: username={}", rotated.getUser().getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildAccessCookie(newAccessToken).toString(),
                        buildRefreshCookie(rotated.getToken()).toString())
                .body(new AuthResponse(
                        rotated.getUser().getUsername(),
                        rotated.getUser().getEmail(),
                        rotated.getUser().getFullName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        extractCookieValue(request, "refresh_token")
                .ifPresent(refreshTokenService::revokeByToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        expiredCookie("access_token").toString(),
                        expiredCookie("refresh_token").toString())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(accessExpiration / 1000)
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth/")
                .maxAge(refreshExpiration / 1000)
                .build();
    }

    private ResponseCookie expiredCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    private Optional<String> extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
