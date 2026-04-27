package com.mali.smartbudget.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.StatementRepository;
import com.mali.smartbudget.repository.TransactionRepository;
import com.mali.smartbudget.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security — JWT Cookie ve Data Isolation Integration Testleri")
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private StatementRepository statementRepository;
    @Autowired private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // Cookie yok → 401
    // =========================================================================

    @Nested
    @DisplayName("Cookie Yok → 401 Unauthorized")
    class NoTokenUnauthorized {

        @Test
        @DisplayName("GET /analytics/summary — cookie yok → 401")
        void analyticsSummary_noCookie_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /analytics/transactions — cookie yok → 401")
        void analyticsTransactions_noCookie_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/transactions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /analytics/subscriptions — cookie yok → 401")
        void analyticsSubscriptions_noCookie_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/subscriptions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /statements/upload — cookie yok → 401")
        void statementsUpload_noCookie_returns401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "PDF".getBytes());
            mockMvc.perform(multipart("/api/v1/statements/upload").file(file))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Auth endpoint'ler public
    // =========================================================================

    @Nested
    @DisplayName("Auth Endpoint'ler Public")
    class AuthEndpointsPublic {

        @Test
        @DisplayName("POST /auth/register — tokensuz erişilebilir → 201 + Set-Cookie header içerir")
        void register_noCookie_accessible_returns201WithCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("pub_reg_user")))
                    .andExpect(status().isCreated())
                    .andExpect(cookie().exists("jwt_token"))
                    .andExpect(jsonPath("$.username").value("pub_reg_user"))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @DisplayName("POST /auth/login — hatalı kimlik bilgisi → uygulama katmanı 401 (mesaj içerir)")
        void login_noCookie_badCredentials_returns401FromAppLayer() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"nonexistent","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("message");
        }

        @Test
        @DisplayName("POST /auth/login — geçerli kullanıcı → 200 + HttpOnly cookie set edilir")
        void login_noCookie_validCredentials_returns200WithCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("login_test_user")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"login_test_user","password":"Test1234!"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("jwt_token"))
                    .andExpect(cookie().httpOnly("jwt_token", true))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    // =========================================================================
    // Geçerli cookie → 200 OK
    // =========================================================================

    @Nested
    @DisplayName("Geçerli Cookie → 200 OK")
    class ValidCookieOk {

        @Test
        @DisplayName("Geçerli JWT cookie ile /analytics/summary → 200, yanıt alanları mevcut")
        void validCookie_analyticsSummary_returns200WithFields() throws Exception {
            String token = registerAndGetToken("valid_usr_1");

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token", token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").exists())
                    .andExpect(jsonPath("$.projectedSpending").exists())
                    .andExpect(jsonPath("$.coachAdvice").isNotEmpty());
        }

        @Test
        @DisplayName("Geçerli JWT cookie ile /analytics/transactions → 200, dizi döner")
        void validCookie_analyticsTransactions_returns200WithArray() throws Exception {
            String token = registerAndGetToken("valid_usr_2");

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Geçerli JWT cookie ile /analytics/subscriptions → 200")
        void validCookie_analyticsSubscriptions_returns200() throws Exception {
            String token = registerAndGetToken("valid_usr_3");

            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .cookie(new Cookie("jwt_token", token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // Geçersiz / bozuk / süresi dolmuş cookie → 401
    // =========================================================================

    @Nested
    @DisplayName("Geçersiz Cookie → 401")
    class InvalidCookieUnauthorized {

        @Test
        @DisplayName("Tamamen sahte (malformed) JWT cookie → 401")
        void malformedCookieToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token", "this.is.not.a.real.jwt")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Süresi dolmuş JWT cookie → 401")
        void expiredCookieToken_returns401() throws Exception {
            String expired = buildExpiredToken("expired_usr");

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token", expired)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Geçerli token Authorization header'da (cookie yok) → filtre görmez → 401")
        void validTokenInAuthHeader_noCookie_returns401() throws Exception {
            String token = registerAndGetToken("bearer_usr");

            // Token geçerli ama cookie yerine Authorization header'da — filtre artık oraya bakmıyor
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Rastgele base64 cookie (geçersiz imza) → 401")
        void randomBase64Cookie_invalidSignature_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token",
                                    "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.INVALID_SIGNATURE")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Veri İzolasyonu
    // =========================================================================

    @Nested
    @DisplayName("Veri İzolasyonu (Data Isolation)")
    class DataIsolation {

        @Test
        @DisplayName("User B, User A'nın işlem listesini göremez")
        void userBCannotSeeUserATransactions() throws Exception {
            String tokenA = registerAndGetToken("iso_userA");
            User userA = userRepository.findByUsername("iso_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Migros", "500.00", "Market", false));
            transactionRepository.save(buildTx(userA, "Netflix", "79.99", "Eğlence", true));

            String tokenB = registerAndGetToken("iso_userB");

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].description").value("Migros"));
        }

        @Test
        @DisplayName("User B'nin toplam harcaması sıfır — User A'nın verisi izole")
        void userBSummaryIsZeroWhenUserAHasData() throws Exception {
            String tokenA = registerAndGetToken("sum_userA");
            User userA = userRepository.findByUsername("sum_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Kira", "12000.00", "Kira", false));
            transactionRepository.save(buildTx(userA, "Market", "1500.00", "Market", false));

            String tokenB = registerAndGetToken("sum_userB");

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token", tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(0));

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .cookie(new Cookie("jwt_token", tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(13500.00));
        }

        @Test
        @DisplayName("Abonelik listesi kullanıcı bazlı izole")
        void subscriptionsScopedToUser() throws Exception {
            String tokenA = registerAndGetToken("sub_userA");
            User userA = userRepository.findByUsername("sub_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Netflix", "79.99", "Eğlence", true));
            transactionRepository.save(buildTx(userA, "Spotify", "49.99", "Eğlence", true));

            String tokenB = registerAndGetToken("sub_userB");

            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .cookie(new Cookie("jwt_token", tokenB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .cookie(new Cookie("jwt_token", tokenA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Üç kullanıcı birbirinden bağımsız veri görür")
        void threeUsers_eachSeesOnlyOwnData() throws Exception {
            String tokenA = registerAndGetToken("three_userA");
            String tokenB = registerAndGetToken("three_userB");
            String tokenC = registerAndGetToken("three_userC");

            User userA = userRepository.findByUsername("three_userA").orElseThrow();
            User userC = userRepository.findByUsername("three_userC").orElseThrow();

            transactionRepository.save(buildTx(userA, "A-Migros", "100.00", "Market", false));
            transactionRepository.save(buildTx(userC, "C-Kira", "5000.00", "Kira", false));
            transactionRepository.save(buildTx(userC, "C-Market", "200.00", "Market", false));

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", tokenB)))
                    .andExpect(jsonPath("$").isEmpty());

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", tokenA)))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].description").value("A-Migros"));

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .cookie(new Cookie("jwt_token", tokenC)))
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // =========================================================================
    // Yardımcı metodlar
    // =========================================================================

    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getCookie("jwt_token").getValue();
    }

    private String registerBody(String username) {
        return """
                {"username":"%s","email":"%s@test.com","password":"Test1234!","fullName":"Test User"}
                """.formatted(username, username);
    }

    private Transaction buildTx(User user, String desc, String amount,
                                String category, boolean isSubscription) {
        return Transaction.builder()
                .user(user)
                .date(LocalDate.of(2026, 4, 1))
                .description(desc)
                .amount(new BigDecimal(amount))
                .category(category)
                .currency("TRY")
                .isSubscription(isSubscription)
                .build();
    }

    private String buildExpiredToken(String username) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000L))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }
}
