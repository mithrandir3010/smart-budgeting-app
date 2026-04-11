package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    static final BigDecimal MONTHLY_LIMIT = new BigDecimal("10000");

    /**
     * Sabit gider kategorileri — gerçekçi tasarruf tavsiyesi üretmek için
     * değiştirilemez kalemler tavsiyenin dışında tutulur.
     */
    private static final Set<String> FIXED_CATEGORIES = Set.of("Kira");

    private final TransactionRepository transactionRepository;

    /**
     * Kullanıcının toplam harcamasını, kategori bazlı dökümünü ve AI koçluk
     * tavsiyesini hesaplar.
     *
     * @param userId Analiz edilecek kullanıcının ID'si
     * @return Toplam harcama, kategori dökümü, uyarı, koçluk tavsiyesi, tahmini harcama
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDto getSummary(Long userId) {
        List<Object[]> rows = transactionRepository.findCategoryTotals(userId);

        Map<String, BigDecimal> categoryBreakdown = new LinkedHashMap<>();
        BigDecimal totalSpending = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String category = row[0] != null ? (String) row[0] : "Diğer";
            BigDecimal total = (BigDecimal) row[1];
            categoryBreakdown.put(category, total);
            totalSpending = totalSpending.add(total);
        }

        String warning = null;
        if (totalSpending.compareTo(MONTHLY_LIMIT) > 0) {
            warning = "Dikkat: Aylık harcamanız %.2f TL ile 10.000 TL limitini aştı!"
                    .formatted(totalSpending);
            log.warn("Kullanıcı {} aylık limiti aştı: {} TL", userId, totalSpending);
        }

        LocalDate today = LocalDate.now();
        int dayOfMonth    = today.getDayOfMonth();
        int daysInMonth   = today.lengthOfMonth();

        BigDecimal dailyRate         = computeDailyRate(totalSpending, dayOfMonth);
        BigDecimal projectedSpending = dailyRate.multiply(BigDecimal.valueOf(daysInMonth))
                                                .setScale(2, RoundingMode.HALF_UP);
        String coachAdvice = buildCoachAdvice(totalSpending, categoryBreakdown,
                                              projectedSpending, dayOfMonth);

        log.info("Analiz tamamlandı. userId={}, toplam={} TL, tahmin={} TL, kategori sayısı={}",
                userId, totalSpending, projectedSpending, categoryBreakdown.size());

        return new AnalyticsSummaryDto(
                totalSpending, categoryBreakdown, warning,
                coachAdvice, MONTHLY_LIMIT, projectedSpending, dailyRate
        );
    }

    // -------------------------------------------------------------------------
    // Günlük harcama hızı
    // -------------------------------------------------------------------------

    BigDecimal computeDailyRate(BigDecimal totalSpending, int dayOfMonth) {
        if (dayOfMonth <= 0 || totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalSpending.divide(BigDecimal.valueOf(dayOfMonth), 2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Koçluk tavsiyesi üretimi
    // -------------------------------------------------------------------------

    String buildCoachAdvice(BigDecimal totalSpending,
                            Map<String, BigDecimal> breakdown,
                            BigDecimal projected,
                            int dayOfMonth) {

        if (totalSpending.compareTo(BigDecimal.ZERO) == 0) {
            return "Henüz harcama verisi yok. Ekstre yükledikçe sana özel koçluk önerileri sunacağım.";
        }

        // En yüksek değişken kategoriyi bul (kira gibi sabit kalemler hariç)
        String topVarCategory = breakdown.entrySet().stream()
                .filter(e -> !FIXED_CATEGORIES.contains(e.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (projected.compareTo(MONTHLY_LIMIT) > 0) {
            BigDecimal overage        = projected.subtract(MONTHLY_LIMIT);
            double     overagePct     = overage.divide(MONTHLY_LIMIT, 4, RoundingMode.HALF_UP)
                                               .multiply(BigDecimal.valueOf(100))
                                               .doubleValue();

            String base = "Bu hızla gidersen ay sonu tahminen %s harcayacaksın — limitini %%%s aşacaksın."
                    .formatted(formatTRY(projected), "%.0f".formatted(overagePct));

            if (topVarCategory != null) {
                BigDecimal topAmount  = breakdown.get(topVarCategory);
                // Ne kadar kesinti yetecek?
                BigDecimal needed = overage.min(topAmount);
                base += " \"%s\" harcamandan %s kısarsan hedefe ulaşırsın."
                        .formatted(topVarCategory, formatTRY(needed));
            }
            return base;
        }

        // Limiti aşmayacak
        BigDecimal remaining = MONTHLY_LIMIT.subtract(projected);
        String base = "Ay sonu tahminin %s — limitinin %s altında kalıyorsun. Harika gidiyorsun!"
                .formatted(formatTRY(projected), formatTRY(remaining));

        if (topVarCategory != null) {
            base += " En yüksek değişken harcaman \"%s\" kategorisinde, gözünü üstünde tut."
                    .formatted(topVarCategory);
        }
        return base;
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    private String formatTRY(BigDecimal amount) {
        return "%,.2f TL".formatted(amount.doubleValue())
                         .replace(",", ".");
    }
}
