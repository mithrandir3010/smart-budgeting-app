package com.mali.smartbudget.controller;

import com.mali.smartbudget.config.PasswordEncoderConfig;
import com.mali.smartbudget.config.SecurityConfig;
import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.security.JwtAuthenticationFilter;
import com.mali.smartbudget.security.JwtService;
import com.mali.smartbudget.service.AnalyticsService;
import com.mali.smartbudget.service.TransactionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import io.github.bucket4j.ConsumptionProbe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AnalyticsController katman testi.
 *
 * <p>Spring Security 6 ile uyumlu test kurulumu:
 * SecurityConfig + PasswordEncoderConfig import edilir, JwtAuthenticationFilter mock'u
 * chain.doFilter() çağırarak pass-through davranışı sergiler.
 * Kimlik doğrulama {@code authentication()} post-processor ile SecurityContext'e enjekte edilir.
 */
@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AnalyticsController — Katman Testleri")
class AnalyticsControllerTest {

    private static final String SUMMARY_URL       = "/api/v1/analytics/summary";
    private static final String TRANSACTIONS_URL  = "/api/v1/analytics/transactions";
    private static final String SUBSCRIPTIONS_URL = "/api/v1/analytics/subscriptions";

    @Autowired private MockMvc mockMvc;
    @MockBean  private AnalyticsService analyticsService;
    @MockBean  private TransactionService transactionService;
    @MockBean  private JwtService jwtService;
    @MockBean  private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean  private UserDetailsService userDetailsService;
    @MockBean  private com.mali.smartbudget.service.RateLimitingService rateLimitingService;

    private User testUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() throws Exception {
        // JwtAuthenticationFilter mock: isteği durdurmaz, chain'e devam eder
        Mockito.doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        ConsumptionProbe allowedProbe = mock(ConsumptionProbe.class);
        when(allowedProbe.isConsumed()).thenReturn(true);
        when(rateLimitingService.tryConsumeApi(anyString())).thenReturn(allowedProbe);

        testUser = User.builder()
                .id(1L)
                .username("mali")
                .email("test@mali.com")
                .password("encoded_pass")
                .fullName("Mali Test Kullanıcısı")
                .role("ROLE_USER")
                .build();
        auth = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    // =========================================================================
    // GET /api/v1/analytics/summary
    // =========================================================================

    @Test
    @DisplayName("200 OK — Limitin altında, uyarısız özet döner")
    void getSummary_underLimit_returns200WithNoWarning() throws Exception {
        when(analyticsService.getSummary(1L)).thenReturn(
                dto(new BigDecimal("6100.00"),
                    Map.of("Kira", new BigDecimal("5000.00"), "Market", new BigDecimal("1100.00")),
                    null, "Harika gidiyorsun!")
        );

        mockMvc.perform(get(SUMMARY_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalSpending").value(6100.00))
                .andExpect(jsonPath("$.warning").doesNotExist())
                .andExpect(jsonPath("$.coachAdvice").isNotEmpty())
                .andExpect(jsonPath("$.monthlyBudget").value(10000.00));
    }

    @Test
    @DisplayName("200 OK — Limit aşılınca uyarı ve koçluk tavsiyesi dolu gelir")
    void getSummary_overLimit_returns200WithWarningAndAdvice() throws Exception {
        when(analyticsService.getSummary(1L)).thenReturn(
                dto(new BigDecimal("12500.00"),
                    Map.of("Kira", new BigDecimal("12000.00"), "Market", new BigDecimal("500.00")),
                    "Dikkat: Aylık harcamanız 12500,00 TL ile 10.000 TL limitini aştı!",
                    "Bu hızla gidersen limiti aşacaksın.")
        );

        mockMvc.perform(get(SUMMARY_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning", containsString("10.000 TL")))
                .andExpect(jsonPath("$.coachAdvice").isNotEmpty());
    }

    @Test
    @DisplayName("200 OK — Hiç işlem yoksa sıfır toplam, boş breakdown döner")
    void getSummary_noTransactions_returns200WithZeroTotal() throws Exception {
        when(analyticsService.getSummary(1L)).thenReturn(
                dto(BigDecimal.ZERO, Map.of(), null, "Henüz veri yok.")
        );

        mockMvc.perform(get(SUMMARY_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(0))
                .andExpect(jsonPath("$.categoryBreakdown").isEmpty())
                .andExpect(jsonPath("$.projectedSpending").exists())
                .andExpect(jsonPath("$.dailyRate").exists());
    }

    @Test
    @DisplayName("userId'nin principal'dan geldiği doğrulanır — request parametresinden alınmaz")
    void getSummary_userId_comesFromPrincipal() throws Exception {
        when(analyticsService.getSummary(1L)).thenReturn(
                dto(BigDecimal.ZERO, Map.of(), null, "OK")
        );

        mockMvc.perform(get(SUMMARY_URL).with(authentication(auth)))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(analyticsService).getSummary(1L);
    }

    // =========================================================================
    // GET /api/v1/analytics/transactions
    // =========================================================================

    @Test
    @DisplayName("200 OK — İşlem listesi DTO olarak döner")
    void getTransactions_withData_returns200AndList() throws Exception {
        List<Transaction> transactions = List.of(
                buildTransaction("Migros", "245.90", "Market"),
                buildTransaction("Kira", "12000.00", "Kira")
        );
        when(transactionService.getTransactionsByUser(1L)).thenReturn(transactions);

        mockMvc.perform(get(TRANSACTIONS_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Migros"))
                .andExpect(jsonPath("$[1].category").value("Kira"));
    }

    @Test
    @DisplayName("200 OK — İşlem yoksa boş liste döner")
    void getTransactions_noTransactions_returns200WithEmptyList() throws Exception {
        when(transactionService.getTransactionsByUser(1L)).thenReturn(List.of());

        mockMvc.perform(get(TRANSACTIONS_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================================
    // GET /api/v1/analytics/subscriptions
    // =========================================================================

    @Test
    @DisplayName("200 OK — Abonelik listesi DTO olarak döner")
    void getSubscriptions_withData_returns200AndList() throws Exception {
        Transaction sub = buildTransaction("Netflix", "89.90", "Eğlence");
        when(transactionService.getSubscriptionsByUser(1L)).thenReturn(List.of(sub));

        mockMvc.perform(get(SUBSCRIPTIONS_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Netflix"));
    }

    @Test
    @DisplayName("200 OK — Abonelik yoksa boş liste döner")
    void getSubscriptions_noSubscriptions_returns200WithEmptyList() throws Exception {
        when(transactionService.getSubscriptionsByUser(1L)).thenReturn(List.of());

        mockMvc.perform(get(SUBSCRIPTIONS_URL).with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================================
    // Yardımcılar
    // =========================================================================

    private AnalyticsSummaryDto dto(BigDecimal total, Map<String, BigDecimal> breakdown,
                                    String warning, String advice) {
        return new AnalyticsSummaryDto(
                total, breakdown, warning, advice,
                new BigDecimal("10000.00"),
                total.multiply(new BigDecimal("3")),
                total.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : total.divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP),
                List.of()
        );
    }

    private Transaction buildTransaction(String description, String amount, String category) {
        return Transaction.builder()
                .user(testUser)
                .date(LocalDate.of(2026, 4, 1))
                .description(description)
                .amount(new BigDecimal(amount))
                .category(category)
                .currency("TRY")
                .build();
    }
}
