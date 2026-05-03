package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.dto.BudgetAlertDto;
import com.mali.smartbudget.repository.TransactionRepository;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /**
     * Sabit gider kategorileri — değiştirilemez kalemler tasarruf tavsiyesinin dışında tutulur.
     */
    private static final Set<String> FIXED_CATEGORIES = Set.of("Kira");

    private final TransactionRepository transactionRepository;
    private final BudgetLimitService    budgetLimitService;
    private final UserRepository        userRepository;

    /**
     * Kullanıcının toplam harcamasını, kategori bazlı dökümünü, AI koçluk
     * tavsiyesini ve bütçe uyarılarını hesaplar.
     *
     * <p>Kullanıcının {@code monthlyBudget} alanı null ise bütçe karşılaştırması
     * yapılmaz; sadece harcama özeti ve genel koçluk tavsiyesi döner.
     *
     * @param userId Analiz edilecek kullanıcının ID'si
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(Long userId) {
        // Kullanıcının opsiyonel aylık bütçe hedefini çek
        BigDecimal monthlyBudget = userRepository.findById(userId)
                .map(com.mali.smartbudget.model.User::getMonthlyBudget)
                .orElse(null);

        List<Object[]> rows = transactionRepository.findCategoryTotals(userId);

        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        BigDecimal totalSpending = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String category = row[0] != null ? (String) row[0] : "Diğer";
            BigDecimal total = (BigDecimal) row[1];
            categoryBreakdown.put(category, total);
            totalSpending = totalSpending.add(total);
        }

        // Bütçe aşım uyarısı — yalnızca kullanıcı bir limit belirlediyse
        String warning = null;
        if (monthlyBudget != null && totalSpending.compareTo(monthlyBudget) > 0) {
            warning = "Dikkat: Aylık harcamanız %.2f TL ile %s limitini aştı!"
                    .formatted(totalSpending, formatBudget(monthlyBudget));
            log.warn("Kullanıcı {} aylık limiti aştı: {} TL (limit: {} TL)",
                    userId, totalSpending, monthlyBudget);
        }

        // Ekstrenin fiili tarih aralığını kullan — bugünün ayın kaçı olduğu değil.
        // Kredi kartı ekstresi her zaman ~30 gündür; gerçek gün sayısıyla bölerek
        // günlük oran ve ay sonu tahmini doğru hesaplanır.
        int statementDays = resolveStatementDays(userId);

        BigDecimal dailyRate         = computeDailyRate(totalSpending, statementDays);
        BigDecimal projectedSpending = dailyRate.multiply(BigDecimal.valueOf(30))
                                                .setScale(2, RoundingMode.HALF_UP);
        String coachAdvice = buildCoachAdvice(totalSpending, categoryBreakdown,
                                              projectedSpending, statementDays, monthlyBudget);

        List<BudgetAlertDto> alerts = budgetLimitService.computeAlerts(userId, categoryBreakdown);

        log.info("Analiz tamamlandı. userId={}, toplam={} TL, tahmin={} TL, bütçe={}, alert={}",
                userId, totalSpending, projectedSpending, monthlyBudget, alerts.size());

        return new AnalyticsSummaryDto(
                totalSpending, categoryBreakdown, warning,
                coachAdvice, monthlyBudget, projectedSpending, dailyRate,
                alerts
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Günlük harcama hızı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kullanıcının işlemlerindeki ilk-son tarih arasındaki gün sayısını döner.
     * Ekstre her zaman ~30 gündür; gerçek aralık 1'den küçük olamaz.
     * Veri yoksa ya da tek günse 30 (standart ekstre süresi) kullanılır.
     */
    private int resolveStatementDays(Long userId) {
        List<Object[]> range = transactionRepository.findDateRange(userId);
        if (range.isEmpty() || range.get(0)[0] == null) return 30;
        LocalDate minDate = (LocalDate) range.get(0)[0];
        LocalDate maxDate = (LocalDate) range.get(0)[1];
        int days = (int) ChronoUnit.DAYS.between(minDate, maxDate) + 1;
        return Math.max(days, 1);
    }

    BigDecimal computeDailyRate(BigDecimal totalSpending, int statementDays) {
        if (statementDays <= 0 || totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpending.divide(BigDecimal.valueOf(statementDays), 2, RoundingMode.HALF_UP);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Koçluk tavsiyesi üretimi
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param monthlyBudget Kullanıcının bütçe hedefi; null ise bütçe karşılaştırması yapılmaz.
     */
    String buildCoachAdvice(BigDecimal totalSpending,
                            Map<String, BigDecimal> breakdown,
                            BigDecimal projected,
                            int dayOfMonth,
                            BigDecimal monthlyBudget) {

        if (totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return "Henüz harcama verisi yok. Ekstre yükledikçe sana özel koçluk önerileri sunacağım.";
        }

        // En yüksek değişken kategoriyi bul (sabit kalemler hariç)
        String topVarCategory = breakdown.entrySet().stream()
                .filter(e -> !FIXED_CATEGORIES.contains(e.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        // Bütçe hedefi tanımlanmamışsa → sadece harcama özeti ver
        if (monthlyBudget == null) {
            String base = "Bu ay toplam " + formatTRY(totalSpending) + " harcadın.";
            if (topVarCategory != null) {
                base += " En fazla harcama \"" + topVarCategory + "\" kategorisinde.";
            }
            base += " Aylık bütçe hedefi belirleyerek harcamalarını daha iyi kontrol altına alabilirsin.";
            return base;
        }

        // Bütçe hedefi var → karşılaştırmalı tavsiye
        if (projected.compareTo(monthlyBudget) > 0) {
            BigDecimal overage   = projected.subtract(monthlyBudget);
            double overagePct    = overage.divide(monthlyBudget, 4, RoundingMode.HALF_UP)
                                          .multiply(BigDecimal.valueOf(100))
                                          .doubleValue();

            String base = "Bu hızla gidersen ay sonu tahminen %s harcayacaksın — limitini %%%s aşacaksın."
                    .formatted(formatTRY(projected), "%.0f".formatted(overagePct));

            if (topVarCategory != null) {
                BigDecimal topAmount = breakdown.get(topVarCategory);
                BigDecimal needed    = overage.min(topAmount);
                base += " \"%s\" harcamandan %s kısarsan hedefe ulaşırsın."
                        .formatted(topVarCategory, formatTRY(needed));
            }
            return base;
        }

        BigDecimal remaining = monthlyBudget.subtract(projected);
        String base = "Ay sonu tahminin %s — limitinin %s altında kalıyorsun. Harika gidiyorsun!"
                .formatted(formatTRY(projected), formatTRY(remaining));

        if (topVarCategory != null) {
            base += " En yüksek değişken harcaman \"%s\" kategorisinde, gözünü üstünde tut."
                    .formatted(topVarCategory);
        }
        return base;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcılar
    // ─────────────────────────────────────────────────────────────────────────

    private String formatTRY(BigDecimal amount) {
        return "%,.2f TL".formatted(amount.doubleValue()).replace(",", ".");
    }

    /** Bütçe limitini tam sayı formatında gösterir: 10000 → "10.000 TL" */
    private String formatBudget(BigDecimal amount) {
        return "%,.0f TL".formatted(amount.doubleValue()).replace(",", ".");
    }
}
