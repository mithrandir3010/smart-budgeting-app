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

/**
 * JWT Filtre Zinciri ve Çok-Kullanıcı Veri İzolasyonu — Integration Testleri.
 *
 * <p><b>Kapsam:</b>
 * <ul>
 *   <li>Token olmadan korumalı endpoint'ler → 401 (Spring Security filtresi)</li>
 *   <li>/api/v1/auth/** public → token olmadan 2xx/uygulama katmanı hatası</li>
 *   <li>Geçerli JWT → 200 OK</li>
 *   <li>Malformed / süresi dolmuş / prefix'siz token → 401</li>
 *   <li>Veri izolasyonu: Kullanıcı A'nın verisi Kullanıcı B'ye görünmez</li>
 * </ul>
 *
 * <p><b>Altyapı:</b> H2 in-memory (test profili), {@code serena.extraction.mock=true}.
 * Uygulama konteksti tüm testler arasında paylaşılır; her testten sonra {@code @AfterEach}
 * ile veriler silinir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security — JWT Filtre ve Data Isolation Integration Testleri")
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private StatementRepository statementRepository;
    @Autowired private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    // ── Her testten sonra tüm veriyi temizle ──────────────────────────────────
    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // Token olmadan korumalı endpoint'ler → 401
    // =========================================================================

    @Nested
    @DisplayName("Token Yok → 401 Unauthorized")
    class NoTokenUnauthorized {

        @Test
        @DisplayName("GET /analytics/summary — token yok → 401")
        void analyticsSummary_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /analytics/transactions — token yok → 401")
        void analyticsTransactions_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/transactions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /analytics/subscriptions — token yok → 401")
        void analyticsSubscriptions_noToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/subscriptions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /statements/upload — token yok → 401")
        void statementsUpload_noToken_returns401() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "PDF".getBytes());
            mockMvc.perform(multipart("/api/v1/statements/upload").file(file))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Auth endpoint'ler public — Spring Security filtresi geçmeli
    // =========================================================================

    @Nested
    @DisplayName("Auth Endpoint'ler Public")
    class AuthEndpointsPublic {

        @Test
        @DisplayName("POST /auth/register — tokensuz erişilebilir → 201 Created")
        void register_noToken_accessible_returns201() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("pub_reg_user")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.username").value("pub_reg_user"));
        }

        @Test
        @DisplayName("POST /auth/login — tokensuz erişilebilir; hata Spring Security'den değil uygulama katmanından gelir")
        void login_noToken_accessible_badCredentials401FromAppLayer() throws Exception {
            // Spring Security filtresi geçmeli → GlobalExceptionHandler 401 dönmeli
            // Fark: GlobalExceptionHandler'ın yanıtında "message" alanı var
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"nonexistent","password":"wrong"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // Spring Security'nin bare 401'ü değil, uygulamamızın JSON 401'ü
            assertThat(result.getResponse().getContentAsString()).contains("message");
        }

        @Test
        @DisplayName("POST /auth/login — geçerli kullanıcı ile tokensuz giriş → 200 OK + JWT döner")
        void login_noToken_validCredentials_returns200WithJwt() throws Exception {
            // Önce kullanıcı kaydet
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("login_test_user")))
                    .andExpect(status().isCreated());

            // Tokensuz login → 200
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"login_test_user","password":"Test1234!"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    // =========================================================================
    // Geçerli token → 200 OK
    // =========================================================================

    @Nested
    @DisplayName("Geçerli Token → 200 OK")
    class ValidTokenOk {

        @Test
        @DisplayName("Geçerli JWT ile /analytics/summary → 200, yanıt alanları mevcut")
        void validToken_analyticsSummary_returns200WithFields() throws Exception {
            String token = registerAndGetToken("valid_usr_1");

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").exists())
                    .andExpect(jsonPath("$.monthlyBudget").value(10000))
                    .andExpect(jsonPath("$.projectedSpending").exists())
                    .andExpect(jsonPath("$.coachAdvice").isNotEmpty());
        }

        @Test
        @DisplayName("Geçerli JWT ile /analytics/transactions → 200, dizi döner")
        void validToken_analyticsTransactions_returns200WithArray() throws Exception {
            String token = registerAndGetToken("valid_usr_2");

            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("Geçerli JWT ile /analytics/subscriptions → 200")
        void validToken_analyticsSubscriptions_returns200() throws Exception {
            String token = registerAndGetToken("valid_usr_3");

            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // Geçersiz / bozuk / süresi dolmuş token → 401
    // =========================================================================

    @Nested
    @DisplayName("Geçersiz Token → 401")
    class InvalidTokenUnauthorized {

        @Test
        @DisplayName("Tamamen sahte (malformed) JWT → 401")
        void malformedToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer this.is.not.a.real.jwt"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Süresi dolmuş JWT → 401")
        void expiredToken_returns401() throws Exception {
            String expired = buildExpiredToken("expired_usr");

            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer " + expired))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("'Bearer ' prefix'i olmayan token → filtre görmez → 401")
        void tokenWithoutBearerPrefix_returns401() throws Exception {
            String token = registerAndGetToken("bare_token_usr");

            // Geçerli bir token ama "Bearer " prefix'i yok → JwtAuthenticationFilter atlar
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Rastgele base64 string (geçersiz imza) → 401")
        void randomBase64Token_invalidSignature_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.INVALID_SIGNATURE"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Veri İzolasyonu — Her kullanıcı yalnızca kendi verisini görür
    // =========================================================================

    @Nested
    @DisplayName("Veri İzolasyonu (Data Isolation)")
    class DataIsolation {

        @Test
        @DisplayName("User B, User A'nın işlem listesini göremez")
        void userBCannotSeeUserATransactions() throws Exception {
            // User A kayıt + işlemleri seed et
            String tokenA = registerAndGetToken("iso_userA");
            User userA = userRepository.findByUsername("iso_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Migros", "500.00", "Market", false));
            transactionRepository.save(buildTx(userA, "Netflix", "79.99", "Eğlence", true));

            // User B kayıt — hiç işlemi yok
            String tokenB = registerAndGetToken("iso_userB");

            // User B'nin işlemleri boş olmalı
            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());

            // User A kendi 2 işlemini görür
            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].description").value("Migros"));
        }

        @Test
        @DisplayName("User B'nin toplam harcaması sıfır — User A'nın verisi izole")
        void userBSummaryIsZeroWhenUserAHasData() throws Exception {
            // User A'ya yüksek harcama ekle
            String tokenA = registerAndGetToken("sum_userA");
            User userA = userRepository.findByUsername("sum_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Kira", "12000.00", "Kira", false));
            transactionRepository.save(buildTx(userA, "Market", "1500.00", "Market", false));

            // User B kayıt
            String tokenB = registerAndGetToken("sum_userB");

            // User B'nin toplam harcaması sıfır
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(0));

            // User A kendi toplamını görür (12000 + 1500 = 13500)
            mockMvc.perform(get("/api/v1/analytics/summary")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpending").value(13500.00));
        }

        @Test
        @DisplayName("Abonelik listesi kullanıcı bazlı izole — User B abonelikleri göremez")
        void subscriptionsScopedToUser() throws Exception {
            String tokenA = registerAndGetToken("sub_userA");
            User userA = userRepository.findByUsername("sub_userA").orElseThrow();
            transactionRepository.save(buildTx(userA, "Netflix", "79.99", "Eğlence", true));
            transactionRepository.save(buildTx(userA, "Spotify", "49.99", "Eğlence", true));

            String tokenB = registerAndGetToken("sub_userB");

            // User B abonelik listesi boş
            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            // User A 2 abonelik görür
            mockMvc.perform(get("/api/v1/analytics/subscriptions")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Üçüncü kullanıcı kendi verisini diğerlerinden bağımsız yönetir")
        void threeUsers_eachSeesOnlyOwnData() throws Exception {
            String tokenA = registerAndGetToken("three_userA");
            String tokenB = registerAndGetToken("three_userB");
            String tokenC = registerAndGetToken("three_userC");

            User userA = userRepository.findByUsername("three_userA").orElseThrow();
            User userC = userRepository.findByUsername("three_userC").orElseThrow();

            transactionRepository.save(buildTx(userA, "A-Migros", "100.00", "Market", false));
            transactionRepository.save(buildTx(userC, "C-Kira", "5000.00", "Kira", false));
            transactionRepository.save(buildTx(userC, "C-Market", "200.00", "Market", false));

            // User B — hiç işlem yok → boş liste
            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(jsonPath("$").isEmpty());

            // User A — 1 işlem
            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].description").value("A-Migros"));

            // User C — 2 işlem
            mockMvc.perform(get("/api/v1/analytics/transactions")
                            .header("Authorization", "Bearer " + tokenC))
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // =========================================================================
    // Yardımcı metodlar
    // =========================================================================

    /**
     * Verilen kullanıcı adıyla register endpoint'ine POST atar ve JWT token döner.
     * Şifre sabit: {@code Test1234!}
     */
    private String registerAndGetToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
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

    /**
     * Test amaçlı süresi dolmuş JWT üretir.
     * Aynı secret key kullanılır — imza geçerlidir fakat token expired'dır.
     */
    private String buildExpiredToken(String username) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000L))    // 2 saat önce oluşturuldu
                .expiration(new Date(System.currentTimeMillis() - 3_600_000L))  // 1 saat önce expire oldu
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }
}
