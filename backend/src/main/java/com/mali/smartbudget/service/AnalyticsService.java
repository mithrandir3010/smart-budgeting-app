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

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(Long userId) {
        return getSummary(userId, null);
    }

    /**
     * Kullanıcının toplam harcamasını, kategori bazlı dökümünü, AI koçluk
     * tavsiyesini ve bütçe uyarılarını hesaplar.
     *
     * @param userId      Analiz edilecek kullanıcının ID'si
     * @param statementId Belirli bir ekstreye filtrelemek için; null ise tüm ekstreler
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(Long userId, Long statementId) {
        BigDecimal monthlyBudget = userRepository.findById(userId)
                .map(com.mali.smartbudget.model.User::getMonthlyBudget)
                .orElse(null);

        List<Object[]> rows = statementId != null
                ? transactionRepository.findCategoryTotalsByStatementId(statementId)
                : transactionRepository.findCategoryTotals(userId);

        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        BigDecimal totalSpending = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String category = row[0] != null ? (String) row[0] : "Diğer";
            BigDecimal total = (BigDecimal) row[1];
            categoryBreakdown.put(category, total);
            totalSpending = totalSpending.add(total);
        }

        // Bütçe aşım uyarısı — ekstre bazlı görünümde gösterilmez
        String warning = null;
        if (statementId == null && monthlyBudget != null && totalSpending.compareTo(monthlyBudget) > 0) {
            warning = "Dikkat: Aylık harcamanız %.2f TL ile %s limitini aştı!"
                    .formatted(totalSpending, formatBudget(monthlyBudget));
            log.warn("Kullanıcı {} aylık limiti aştı: {} TL (limit: {} TL)",
                    userId, totalSpending, monthlyBudget);
        }

        // Tek ekstre = 1 ay = 30 gün; genel özette gerçek ay sayısı kullanılır
        long distinctMonths = statementId != null ? 1L : transactionRepository.countDistinctMonths(userId);
        int statementDays = (int) Math.max(1, distinctMonths) * 30;
        BigDecimal dailyRate = computeDailyRate(totalSpending, statementDays);
        BigDecimal projectedSpending = dailyRate.multiply(BigDecimal.valueOf(30))
                .setScale(2, RoundingMode.HALF_UP);

        String coachAdvice = buildCoachAdvice(totalSpending, categoryBreakdown, monthlyBudget);

        // Bütçe uyarıları kullanıcı genelinde hesaplanır (ekstre bazlı değil)
        List<BudgetAlertDto> alerts = statementId == null
                ? budgetLimitService.computeAlerts(userId, categoryBreakdown)
                : List.of();

        log.info("Analiz tamamlandı. userId={}, statementId={}, toplam={} TL, tahmin={} TL",
                userId, statementId, totalSpending, projectedSpending);

        return new AnalyticsSummaryDto(
                totalSpending, categoryBreakdown, warning,
                coachAdvice, monthlyBudget, projectedSpending, dailyRate,
                alerts
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Günlük harcama hızı
    // ─────────────────────────────────────────────────────────────────────────

BigDecimal computeDailyRate(BigDecimal totalSpending, int statementDays) {
        if (statementDays <= 0 || totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpending.divide(BigDecimal.valueOf(statementDays), 2, RoundingMode.HALF_UP);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Koçluk tavsiyesi üretimi
    // ─────────────────────────────────────────────────────────────────────────

    String buildCoachAdvice(BigDecimal totalSpending,
                            Map<String, BigDecimal> breakdown,
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
            String base = "Toplam " + formatTRY(totalSpending) + " harcadın.";
            if (topVarCategory != null) {
                base += " En fazla harcama \"" + topVarCategory + "\" kategorisinde.";
            }
            base += " Aylık bütçe hedefi belirleyerek harcamalarını daha iyi kontrol altına alabilirsin.";
            return base;
        }

        // Bütçe hedefi var → karşılaştırmalı tavsiye
        if (totalSpending.compareTo(monthlyBudget) > 0) {
            BigDecimal overage   = totalSpending.subtract(monthlyBudget);
            double overagePct    = overage.divide(monthlyBudget, 4, RoundingMode.HALF_UP)
                                          .multiply(BigDecimal.valueOf(100))
                                          .doubleValue();

            String base = "Şu ana kadar %s harcadın — limitini %%%s aşıyorsun."
                    .formatted(formatTRY(totalSpending), "%.0f".formatted(overagePct));

            if (topVarCategory != null) {
                BigDecimal topAmount = breakdown.get(topVarCategory);
                BigDecimal needed    = overage.min(topAmount);
                base += " \"%s\" harcamandan %s kısarsan hedefe ulaşırsın."
                        .formatted(topVarCategory, formatTRY(needed));
            }
            return base;
        }

        BigDecimal remaining = monthlyBudget.subtract(totalSpending);
        String base = "Şu ana kadar %s harcadın — limitinin %s altındasın. Harika gidiyorsun!"
                .formatted(formatTRY(totalSpending), formatTRY(remaining));

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
