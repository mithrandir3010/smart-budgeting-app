package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AuthResponse;
import com.mali.smartbudget.dto.AuthTokenResult;
import com.mali.smartbudget.dto.LoginRequest;
import com.mali.smartbudget.dto.RegisterRequest;
import com.mali.smartbudget.service.AuthService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Kayıt isteği: username={}", request.username());
        AuthTokenResult result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, buildJwtCookie(result.token()).toString())
                .body(result.userInfo());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Giriş isteği: username={}", request.username());
        AuthTokenResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildJwtCookie(result.token()).toString())
                .body(result.userInfo());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie expired = ResponseCookie.from("jwt_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }

    private ResponseCookie buildJwtCookie(String token) {
        return ResponseCookie.from("jwt_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwtExpiration / 1000)
                .build();
    }
}
