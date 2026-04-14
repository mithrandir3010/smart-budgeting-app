package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AuthResponse;
import com.mali.smartbudget.dto.LoginRequest;
import com.mali.smartbudget.dto.RegisterRequest;
import com.mali.smartbudget.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

    /**
     * Yeni kullanıcı kaydı.
     *
     * <pre>
     * POST /api/v1/auth/register
     * { "username": "mali", "email": "mali@example.com",
     *   "password": "secret123", "fullName": "Mali Kullanıcı" }
     * </pre>
     *
     * @return 201 Created + AuthResponse (token, username, email, fullName)
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Kayıt isteği: username={}", request.username());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Kullanıcı girişi ve JWT token alma.
     *
     * <pre>
     * POST /api/v1/auth/login
     * { "username": "mali", "password": "secret123" }
     * </pre>
     *
     * @return 200 OK + AuthResponse (token, username, email, fullName)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Giriş isteği: username={}", request.username());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
