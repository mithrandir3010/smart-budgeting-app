package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.TransactionRepository;
import com.mali.smartbudget.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService — Birim Testleri")
class AnalyticsServiceTest {

    private static final Long USER_ID = 1L;

    @Mock  private TransactionRepository transactionRepository;
    @Mock  private BudgetLimitService    budgetLimitService;
    @Mock  private UserRepository        userRepository;
    @InjectMocks private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        // Varsayılan: boş uyarı listesi ve bütçesiz kullanıcı
        org.mockito.Mockito.lenient()
                .when(budgetLimitService.computeAlerts(anyLong(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(userRepository.findById(anyLong()))
                .thenReturn(Optional.empty());  // monthlyBudget → null
    }

    // ── Yardımcı: belirli bütçeyle kullanıcı stub'u ──────────────────────────

    private void stubUserBudget(BigDecimal budget) {
        User u = User.builder().id(USER_ID).monthlyBudget(budget).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(u));
    }

    // =========================================================================
    // Toplam harcama ve kategori dökümü
    // =========================================================================

    @Test
    @DisplayName("Hiç işlem yoksa toplam sıfır, breakdown boş, uyarı yok")
    void getSummary_noTransactions_returnsZeroTotalsAndNoWarning() {
        stubUserBudget(new BigDecimal("10000"));
        when(transactionRepository.findCategoryTotals(USER_ID)).thenReturn(rows());

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.totalSpending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.categoryBreakdown()).isEmpty();
        assertThat(result.warning()).isNull();
        assertThat(result.monthlyBudget()).isEqualByComparingTo("10000");
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
    // Aylık bütçe senaryoları
    // =========================================================================

    @Test
    @DisplayName("Harcama tam limitte (10.000 TL) — uyarı tetiklenmez")
    void getSummary_spendingExactlyAtLimit_noWarning() {
        stubUserBudget(new BigDecimal("10000"));
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "10000.00")));

        assertThat(analyticsService.getSummary(USER_ID).warning()).isNull();
    }

    @Test
    @DisplayName("Harcama limiti bir kuruş aştığında uyarı verilir")
    void getSummary_spendingOneAboveLimit_warningTriggered() {
        stubUserBudget(new BigDecimal("10000"));
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "10000.01")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNotNull();
        assertThat(result.warning()).contains("10.000 TL");
    }

    @Test
    @DisplayName("Yüksek harcamada uyarı mesajı doğru toplam tutarı içerir")
    void getSummary_overLimit_warningContainsTotalAmount() {
        stubUserBudget(new BigDecimal("10000"));
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "12000.00"), row("Market", "500.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNotNull();
        assertThat(result.totalSpending()).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("Harcama limitin altındaysa uyarı null döner")
    void getSummary_underLimit_noWarning() {
        stubUserBudget(new BigDecimal("10000"));
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kafe", "350.00")));

        assertThat(analyticsService.getSummary(USER_ID).warning()).isNull();
    }

    @Test
    @DisplayName("monthlyBudget null ise bütçe uyarısı hiç üretilmez")
    void getSummary_nullBudget_noWarningEvenWhenHighSpending() {
        // userRepository.findById → empty (null budget) — setUp'ta default olarak ayarlandı
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kira", "99999.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.warning()).isNull();
        assertThat(result.monthlyBudget()).isNull();
    }

    // =========================================================================
    // Uç senaryolar
    // =========================================================================

    @Test
    @DisplayName("DB'den gelen null kategori adı 'Diğer' olarak eşleştirilir")
    void getSummary_nullCategoryFromDb_mappedToDiger() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row(null, "200.00")));

        assertThat(analyticsService.getSummary(USER_ID).categoryBreakdown()).containsKey("Diğer");
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

    // =========================================================================
    // computeDailyRate
    // =========================================================================

    @Test
    @DisplayName("Sıfır harcamada günlük oran sıfır döner")
    void computeDailyRate_zeroSpending_returnsZero() {
        assertThat(analyticsService.computeDailyRate(BigDecimal.ZERO, 10))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("dayOfMonth sıfır veya negatifse günlük oran sıfır döner")
    void computeDailyRate_zeroDayOfMonth_returnsZero() {
        assertThat(analyticsService.computeDailyRate(new BigDecimal("5000"), 0))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Ayın 10'unda 5.000 TL harcanmışsa günlük oran 500 TL'dir")
    void computeDailyRate_day10_5000Spent_returns500() {
        assertThat(analyticsService.computeDailyRate(new BigDecimal("5000.00"), 10))
                .isEqualByComparingTo("500.00");
    }

    // =========================================================================
    // buildCoachAdvice
    // =========================================================================

    @Test
    @DisplayName("Harcama yoksa 'veri yok' mesajı döner")
    void buildCoachAdvice_noSpending_returnsNoDataMessage() {
        String advice = analyticsService.buildCoachAdvice(
                BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, 10, null
        );
        assertThat(advice).contains("yok");
    }

    @Test
    @DisplayName("Bütçe null iken tavsiye harcama özetini ve bütçe teşvikini içerir")
    void buildCoachAdvice_nullBudget_returnsSpendingSummary() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("3000.00"),
                Map.of("Market", new BigDecimal("3000.00")),
                new BigDecimal("9000.00"),
                10,
                null
        );
        assertThat(advice).contains("harcadın");
        assertThat(advice).contains("Market");
        assertThat(advice).contains("bütçe hedefi");
    }

    @Test
    @DisplayName("Tahmini harcama limiti aşıyorsa tavsiye aşım yüzdesini içerir")
    void buildCoachAdvice_projectedOverLimit_containsOveragePercent() {
        // Projected = 20.000 TL → %100 aşım (limit = 10.000)
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("6666.67"),
                Map.of("Market", new BigDecimal("6666.67")),
                new BigDecimal("20000.00"),
                10,
                new BigDecimal("10000")
        );
        assertThat(advice).contains("Market");
        assertThat(advice).contains("aşacaksın");
    }

    @Test
    @DisplayName("Tahmini harcama limit altındaysa pozitif mesaj döner")
    void buildCoachAdvice_projectedUnderLimit_returnsPositiveMessage() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("2000.00"),
                Map.of("Market", new BigDecimal("2000.00")),
                new BigDecimal("6000.00"),
                10,
                new BigDecimal("10000")
        );
        assertThat(advice).contains("altında");
        assertThat(advice).contains("Market");
    }

    @Test
    @DisplayName("Kira sabit kategori olarak tavsiyeye dahil edilmez")
    void buildCoachAdvice_onlyFixedCategories_adviceExcludesKira() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("12000.00"),
                Map.of("Kira", new BigDecimal("12000.00")),
                new BigDecimal("36000.00"),
                10,
                new BigDecimal("10000")
        );
        assertThat(advice).doesNotContain("Kira\" harcamandan");
    }

    @Test
    @DisplayName("getSummary yanıtı projectedSpending ve dailyRate alanlarını içerir")
    void getSummary_returnsProjectionFields() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Market", "3000.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.projectedSpending()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.dailyRate()).isNotNull().isGreaterThan(BigDecimal.ZERO);
        assertThat(result.coachAdvice()).isNotNull().isNotBlank();
    }

    // =========================================================================
    // computeDailyRate — Ek Senaryolar
    // =========================================================================

    @Test
    @DisplayName("dayOfMonth negatifse günlük oran sıfır döner (guard kontrolü)")
    void computeDailyRate_negativeDayOfMonth_returnsZero() {
        assertThat(analyticsService.computeDailyRate(new BigDecimal("5000"), -1))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Tam bölünmeyen değerlerde HALF_UP yuvarlama uygulanır (1000 / 3 = 333.33)")
    void computeDailyRate_nonDivisibleValue_appliesHalfUpRounding() {
        assertThat(analyticsService.computeDailyRate(new BigDecimal("1000.00"), 3))
                .isEqualByComparingTo("333.33");
    }

    @Test
    @DisplayName("Ayın son günündeyse günlük oran ≈ toplam harcama (30 günlük ay, 30. gün)")
    void computeDailyRate_lastDayOf30DayMonth_rateEqualsSpending() {
        BigDecimal total = new BigDecimal("3000.00");
        assertThat(analyticsService.computeDailyRate(total, 30))
                .isEqualByComparingTo("100.00");
    }

    // =========================================================================
    // buildCoachAdvice — Detaylı İçerik ve Metin Testleri
    // =========================================================================

    @Test
    @DisplayName("Limit aşılıyorsa mesajda kategori adı ve 'kısarsan' ifadesi geçer")
    void buildCoachAdvice_projectedOverLimit_messageContainsCategoryAndKisarsan() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("5000.00"),
                Map.of("Market", new BigDecimal("5000.00")),
                new BigDecimal("15000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("Market");
        assertThat(advice).contains("kısarsan");
        assertThat(advice).contains("aşacaksın");
    }

    @Test
    @DisplayName("Yalnızca sabit kategori (Kira) varsa 'kısarsan' tavsiyesi eklenmez")
    void buildCoachAdvice_projectedOverLimit_onlyFixedCategory_noKisarsan() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("12000.00"),
                Map.of("Kira", new BigDecimal("12000.00")),
                new BigDecimal("36000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).doesNotContain("kısarsan");
        assertThat(advice).contains("aşacaksın");
    }

    @Test
    @DisplayName("Limit altındaysa mesajda kalan bütçe miktarı geçer")
    void buildCoachAdvice_projectedUnderLimit_messageContainsRemainingAmount() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("2000.00"),
                Map.of("Market", new BigDecimal("2000.00")),
                new BigDecimal("6000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("altında");
        assertThat(advice).contains("TL");
        assertThat(advice).contains("Harika");
    }

    @Test
    @DisplayName("Tahmini harcama tam limitte (10.000) — 'altında kalıyorsun' mesajı döner")
    void buildCoachAdvice_projectedExactlyAtLimit_returnsPositiveMessage() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("3333.34"),
                Map.of("Market", new BigDecimal("3333.34")),
                new BigDecimal("10000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("altında");
        assertThat(advice).doesNotContain("aşacaksın");
    }

    @Test
    @DisplayName("Birden fazla değişken kategori varsa en yüksek değerli olanı seçilir")
    void buildCoachAdvice_multipleVariableCategories_selectsHighestByValue() {
        Map<String, BigDecimal> breakdown = Map.of(
                "Kafe",    new BigDecimal("500.00"),
                "Market",  new BigDecimal("2000.00"),
                "Ulaşım",  new BigDecimal("800.00")
        );
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("3300.00"),
                breakdown,
                new BigDecimal("9900.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("Market");
        assertThat(advice).doesNotContain("Kafe");
    }

    @Test
    @DisplayName("Limit aşılıyorsa mesajda tahmini ve aşım yüzdesi bulunur")
    void buildCoachAdvice_projectedOverLimit_containsProjectedAndOveragePercent() {
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("6666.67"),
                Map.of("Restoran", new BigDecimal("6666.67")),
                new BigDecimal("20000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("20");
        assertThat(advice).contains("100");
        assertThat(advice).contains("aşacaksın");
    }

    @Test
    @DisplayName("Kira + değişken kategori karması: Kira hariç tutulur, değişken kategori seçilir")
    void buildCoachAdvice_mixedCategories_kiraExcludedFromVariableSelection() {
        Map<String, BigDecimal> breakdown = Map.of(
                "Kira",   new BigDecimal("8000.00"),
                "Market", new BigDecimal("3000.00")
        );
        String advice = analyticsService.buildCoachAdvice(
                new BigDecimal("11000.00"),
                breakdown,
                new BigDecimal("33000.00"),
                10,
                new BigDecimal("10000")
        );

        assertThat(advice).contains("Market");
        assertThat(advice).doesNotContain("\"Kira\" harcamandan");
    }

    // =========================================================================
    // getSummary — Projeksiyon Alanları
    // =========================================================================

    @Test
    @DisplayName("projectedSpending her zaman totalSpending'e eşit ya da büyüktür")
    void getSummary_projectedSpending_alwaysAtLeastEqualToTotal() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Market", "3000.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.projectedSpending())
                .isGreaterThanOrEqualTo(result.totalSpending());
    }

    @Test
    @DisplayName("Sıfır harcamada projected ve dailyRate sıfırdır")
    void getSummary_zeroSpending_projectedAndRateAreZero() {
        when(transactionRepository.findCategoryTotals(USER_ID)).thenReturn(rows());

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.projectedSpending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.dailyRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Sıfır olmayan harcamada dailyRate pozitiftir")
    void getSummary_nonZeroSpending_dailyRateIsPositive() {
        when(transactionRepository.findCategoryTotals(USER_ID))
                .thenReturn(rows(row("Kafe", "500.00")));

        AnalyticsSummaryDto result = analyticsService.getSummary(USER_ID);

        assertThat(result.dailyRate()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.projectedSpending()).isGreaterThan(BigDecimal.ZERO);
    }

    // =========================================================================
    // Yardımcılar
    // =========================================================================

    private Object[] row(String category, String amount) {
        return new Object[]{category, new BigDecimal(amount)};
    }

    @SafeVarargs
    private List<Object[]> rows(Object[]... entries) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] entry : entries) list.add(entry);
        return list;
    }
}
