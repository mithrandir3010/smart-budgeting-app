package com.mali.smartbudget.controller;

import com.mali.smartbudget.model.RefreshToken;
import com.mali.smartbudget.repository.RefreshTokenRepository;
import com.mali.smartbudget.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Auth — Logout ve Refresh Token Integration Testleri")
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // Logout
    // =========================================================================

    @Nested
    @DisplayName("POST /auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("DB'deki refresh token revoked=true olarak işaretlenir")
        void logout_setsRefreshTokenRevokedInDatabase() throws Exception {
            MvcResult result = performRegister("logout_db_user");
            String refreshTokenValue = extractRefreshToken(result);

            RefreshToken before = refreshTokenRepository.findByToken(refreshTokenValue).orElseThrow();
            assertThat(before.isRevoked()).isFalse();

            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refresh_token", refreshTokenValue)))
                    .andExpect(status().isNoContent());

            RefreshToken after = refreshTokenRepository.findByToken(refreshTokenValue).orElseThrow();
            assertThat(after.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("access_token ve refresh_token cookie'leri sıfırlanır (maxAge=0)")
        void logout_clearsBothAuthCookies() throws Exception {
            MvcResult result = performRegister("logout_cookie_user");
            String refreshTokenValue = extractRefreshToken(result);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refresh_token", refreshTokenValue)))
                    .andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge("access_token", 0))
                    .andExpect(cookie().maxAge("refresh_token", 0));
        }

        @Test
        @DisplayName("refresh_token cookie'si olmadan çağrılırsa yine 204 döner (idempotent)")
        void logout_withoutRefreshCookie_returnsNoContent() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Logout sonrası aynı refresh token /refresh endpoint'ine gönderilirse 401 döner")
        void logout_revokedTokenCannotBeUsedToRefresh() throws Exception {
            MvcResult result = performRegister("logout_refresh_user");
            String refreshTokenValue = extractRefreshToken(result);

            // Token'ı iptal et
            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refresh_token", refreshTokenValue)))
                    .andExpect(status().isNoContent());

            // İptal edilmiş token ile yenileme denemesi → 401
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", refreshTokenValue)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Refresh token iptal edilmiş."));
        }
    }

    // =========================================================================
    // Refresh (token rotation)
    // =========================================================================

    @Nested
    @DisplayName("POST /auth/refresh")
    class RefreshTests {

        @Test
        @DisplayName("Geçerli refresh token → 200, yeni access_token ve refresh_token cookie set edilir")
        void refresh_withValidToken_setsNewCookies() throws Exception {
            MvcResult result = performRegister("refresh_rotate_user");
            String refreshTokenValue = extractRefreshToken(result);

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", refreshTokenValue)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("access_token"))
                    .andExpect(cookie().exists("refresh_token"))
                    .andExpect(jsonPath("$.username").value("refresh_rotate_user"));
        }

        @Test
        @DisplayName("Eski refresh token rotation sonrası geçersiz hale gelir")
        void refresh_oldTokenIsRevokedAfterRotation() throws Exception {
            MvcResult result = performRegister("refresh_old_token_user");
            String oldRefreshToken = extractRefreshToken(result);

            // Rotate
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", oldRefreshToken)))
                    .andExpect(status().isOk());

            // Eski token ile tekrar deneme → 401 (revoked)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", oldRefreshToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("refresh_token cookie'si olmadan çağrılırsa 401 döner")
        void refresh_withoutCookie_returnsUnauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Yardımcı metodlar
    // =========================================================================

    private MvcResult performRegister(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@test.com","password":"Test1234!","fullName":"Test User"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String extractRefreshToken(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).as("refresh_token cookie mevcut olmalı").isNotNull();
        return cookie.getValue();
    }
}
