package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.AnalyticsService;
import com.mali.smartbudget.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@DisplayName("AnalyticsController — MockMvc Testleri")
class AnalyticsControllerTest {

    private static final String SUMMARY_URL     = "/api/v1/analytics/summary";
    private static final String TRANSACTIONS_URL = "/api/v1/analytics/transactions";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AnalyticsService analyticsService;
    @MockBean private TransactionService transactionService;

    // =========================================================================
    // GET /api/v1/analytics/summary
    // =========================================================================

    @Test
    @DisplayName("200 OK — Limitin altında, uyarısız özet döner")
    void getSummary_underLimit_returns200WithNoWarning() throws Exception {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto(
                new BigDecimal("6100.00"),
                Map.of("Kira", new BigDecimal("5000.00"), "Market", new BigDecimal("1100.00")),
                null
        );
        when(analyticsService.getSummary(1L)).thenReturn(dto);

        mockMvc.perform(get(SUMMARY_URL).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalSpending").value(6100.00))
                .andExpect(jsonPath("$.warning").doesNotExist());
    }

    @Test
    @DisplayName("200 OK — Limit aşılınca uyarı alanı dolu gelir")
    void getSummary_overLimit_returns200WithWarning() throws Exception {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto(
                new BigDecimal("12500.00"),
                Map.of("Kira", new BigDecimal("12000.00"), "Market", new BigDecimal("500.00")),
                "Dikkat: Aylık harcamanız 12500,00 TL ile 10.000 TL limitini aştı!"
        );
        when(analyticsService.getSummary(1L)).thenReturn(dto);

        mockMvc.perform(get(SUMMARY_URL).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").isNotEmpty())
                .andExpect(jsonPath("$.warning", containsString("10.000 TL")));
    }

    @Test
    @DisplayName("200 OK — Hiç işlem olmayan kullanıcı için sıfır toplam döner")
    void getSummary_noTransactions_returns200WithZeroTotal() throws Exception {
        AnalyticsSummaryDto dto = new AnalyticsSummaryDto(
                BigDecimal.ZERO, Map.of(), null
        );
        when(analyticsService.getSummary(1L)).thenReturn(dto);

        mockMvc.perform(get(SUMMARY_URL).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(0))
                .andExpect(jsonPath("$.categoryBreakdown").isEmpty());
    }

    @Test
    @DisplayName("400 Bad Request — userId parametresi eksik")
    void getSummary_missingUserId_returns400() throws Exception {
        mockMvc.perform(get(SUMMARY_URL))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/v1/analytics/transactions
    // =========================================================================

    @Test
    @DisplayName("200 OK — İşlem listesi DTO olarak döner")
    void getTransactions_withData_returns200AndList() throws Exception {
        User user = User.builder().id(1L).email("x@x.com").fullName("X").password("p").build();

        List<Transaction> transactions = List.of(
                Transaction.builder()
                        .user(user).date(LocalDate.of(2026, 4, 1))
                        .description("Migros").amount(new BigDecimal("245.90"))
                        .category("Market").currency("TRY").build(),
                Transaction.builder()
                        .user(user).date(LocalDate.of(2026, 4, 3))
                        .description("Kira").amount(new BigDecimal("12000.00"))
                        .category("Kira").currency("TRY").build()
        );
        when(transactionService.getTransactionsByUser(1L)).thenReturn(transactions);

        mockMvc.perform(get(TRANSACTIONS_URL).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Migros"))
                .andExpect(jsonPath("$[1].category").value("Kira"));
    }

    @Test
    @DisplayName("200 OK — İşlem yoksa boş liste döner")
    void getTransactions_noTransactions_returns200WithEmptyList() throws Exception {
        when(transactionService.getTransactionsByUser(1L)).thenReturn(List.of());

        mockMvc.perform(get(TRANSACTIONS_URL).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("400 Bad Request — userId parametresi eksik")
    void getTransactions_missingUserId_returns400() throws Exception {
        mockMvc.perform(get(TRANSACTIONS_URL))
                .andExpect(status().isBadRequest());
    }
}
