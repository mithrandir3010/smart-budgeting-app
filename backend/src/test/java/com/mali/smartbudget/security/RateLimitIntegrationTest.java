package com.mali.smartbudget.security;

import com.mali.smartbudget.repository.EmailVerificationTokenRepository;
import com.mali.smartbudget.repository.RefreshTokenRepository;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.service.EmailService;
import com.mali.smartbudget.service.RateLimitingService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.rate-limit.enabled=true",
                "app.rate-limit.capacity=3",
                "app.rate-limit.refill-minutes=5",
                "app.rate-limit.register-capacity=3",
                "app.rate-limit.register-refill-hours=1"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Rate Limiting — Brute-force Koruması Entegrasyon Testleri")
class RateLimitIntegrationTest {

    private static final String BAD_LOGIN = "{\"username\":\"nonexistent\",\"password\":\"wrong\"}";

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitingService rateLimitingService;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired UserRepository userRepository;

    @MockBean EmailService emailService;

    @BeforeEach
    void setUp() {
        rateLimitingService.clearAll();
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("3 deneme limit dahilinde geçer (non-429), 4. deneme 429 + JSON body döner")
    void rateLimitExceeded_returns429WithBody() throws Exception {
        String testIp = "10.0.0.1";

        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .with(req -> { req.setRemoteAddr(testIp); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BAD_LOGIN))
                    .andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("İstek %d rate limit'e çarpmamalı", i + 1)
                    .isNotEqualTo(429);
        }

        // 4. istek → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Farklı IP'ler bağımsız kotalara sahiptir")
    void differentIPs_haveIndependentBuckets() throws Exception {
        String ip1 = "10.0.0.2";
        String ip2 = "10.0.0.3";

        // IP1 limitini tüket
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(req -> { req.setRemoteAddr(ip1); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BAD_LOGIN));
        }
        // IP1 → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(ip1); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andExpect(status().isTooManyRequests());

        // IP2 hâlâ çalışmalı
        MvcResult ip2Result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(ip2); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andReturn();
        assertThat(ip2Result.getResponse().getStatus()).isNotEqualTo(429);
    }

    @Test
    @DisplayName("Register endpoint'i de rate limit kapsamındadır")
    void registerEndpoint_isAlsoRateLimited() throws Exception {
        String testIp = "10.0.0.4";

        // Kapasiteyi tüket
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            mockMvc.perform(post("/api/v1/auth/register")
                    .with(req -> { req.setRemoteAddr(testIp); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"rluser" + idx + "\",\"email\":\"rl" + idx
                            + "@test.com\",\"password\":\"Test1234!\",\"fullName\":\"RL\"}"));
        }

        // 4. register → 429
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rluserfail\",\"email\":\"rlfail@test.com\","
                                + "\"password\":\"Test1234!\",\"fullName\":\"RL\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @DisplayName("X-RateLimit-Remaining header token azaldıkça düşer")
    void xRateLimitRemainingHeader_decreasesWithEachRequest() throws Exception {
        String testIp = "10.0.0.5";

        // 1. istek → remaining = 2  (kapasite 3, 1 tüketildi)
        MvcResult first = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andReturn();
        assertThat(first.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("2");

        // 2. istek → remaining = 1
        MvcResult second = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andReturn();
        assertThat(second.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("1");

        // 3. istek → remaining = 0
        MvcResult third = mockMvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr(testIp); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andReturn();
        assertThat(third.getResponse().getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    @DisplayName("X-Forwarded-For header'ı gerçek IP olarak kullanılır")
    void xForwardedFor_usedAsClientIp() throws Exception {
        String proxiedIp = "203.0.113.42";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", proxiedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BAD_LOGIN));
        }

        // Aynı forwarded IP → 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", proxiedIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andExpect(status().isTooManyRequests());

        // Farklı forwarded IP → serbest
        MvcResult other = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BAD_LOGIN))
                .andReturn();
        assertThat(other.getResponse().getStatus()).isNotEqualTo(429);
    }
}
