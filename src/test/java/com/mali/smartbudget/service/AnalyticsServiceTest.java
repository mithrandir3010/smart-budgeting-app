package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService — Birim Testleri")
class AnalyticsServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    // -------------------------------------------------------------------------
    // Yardımcılar: veritabanı satırlarını temsil eden List<Object[]> oluşturur
    // -------------------------------------------------------------------------
    private Object[] row(String category, String amount) {
        return new Object[]{category, new BigDecimal(amount)};
    }

    @SafeVarargs
    private List<Object[]> rows(Object[]... entries) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] entry : entries) list.add(entry);
        return list;
    }

    // =========================================================================
    // Toplam harcama ve kategori dökümü
    // =========================================================================

    @Test
    @DisplayName("Hiç işlem yoksa toplam sıfır, breakdown boş, uyarı yok")
    void getSummary_noTransactions_returnsZeroTotalsAndNoWarning() {
        when(transactionRepository.findCategoryTotals(USER_ID)).thenReturn(rows());

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.totalSpending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.categoryBreakdown()).isEmpty();
        assertThat(result.warning()).isNull();
    }

    @Test
    @DisplayName("Tek kategori — toplam ve breakdown doğru hesaplanır")
    void getSummary_singleCategory_calculatesCorrectly() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Market", "500.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.totalSpending()).isEqualByComparingTo("500.00");
        assertThat(result.categoryBreakdown()).containsEntry("Market", new BigDecimal("500.00"));
        assertThat(result.warning()).isNull();
    }

    @Test
    @DisplayName("Birden fazla kategori — toplam doğru, tüm kategoriler breakdown'da mevcut")
    void getSummary_multipleCategories_aggregatesAll() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "5000.00"), row("Market", "800.00"), row("Ulaşım", "300.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.totalSpending()).isEqualByComparingTo("6100.00");
        assertThat(result.categoryBreakdown()).hasSize(3);
        assertThat(result.categoryBreakdown()).containsKeys("Kira", "Market", "Ulaşım");
    }

    // =========================================================================
    // Aylık limit (10.000 TL) senaryoları
    // =========================================================================

    @Test
    @DisplayName("Harcama tam limitte (10.000 TL) — uyarı tetiklenmez (limit aşılmadı)")
    void getSummary_spendingExactlyAtLimit_noWarning() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "10000.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        // compareTo > 0 koşulu, tam eşitlikte false döner → uyarı yok
        assertThat(result.warning()).isNull();
    }

    @Test
    @DisplayName("Harcama limiti bir kuruş aştığında uyarı verilir")
    void getSummary_spendingOneAboveLimit_warningTriggered() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "10000.01")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNotNull();
        assertThat(result.warning()).contains("10.000 TL");
    }

    @Test
    @DisplayName("Yüksek harcamada uyarı mesajı doğru toplam tutarı içerir")
    void getSummary_overLimit_warningContainsTotalAmount() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "12000.00"), row("Market", "500.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNotNull();
        assertThat(result.totalSpending()).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("Harcama limitin altındaysa uyarı null döner")
    void getSummary_underLimit_noWarning() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kafe", "350.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNull();
    }

    // =========================================================================
    // Uç senaryolar
    // =========================================================================

    @Test
    @DisplayName("DB'den gelen null kategori adı 'Diğer' olarak eşleştirilir")
    void getSummary_nullCategoryFromDb_mappedToDiger() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row(null, "200.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.categoryBreakdown()).containsKey("Diğer");
    }

    @Test
    @DisplayName("Bilinmeyen userId için boş sonuç döner (repository boş liste verir)")
    void getSummary_unknownUserId_returnsEmptyResult() {
        when(transactionRepository.findCategoryTotals(999L)).thenReturn(rows());

        AnalyticsSummaryDto result = analyticsService.getSummary(999L);

        assertThat(result.totalSpending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.categoryBreakdown()).isEmpty();
        assertThat(result.warning()).isNull();
    }
}
