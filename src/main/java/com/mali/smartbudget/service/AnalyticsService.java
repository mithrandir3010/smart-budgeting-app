package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final BigDecimal MONTHLY_LIMIT = new BigDecimal("10000");

    private final TransactionRepository transactionRepository;

    /**
     * Kullanıcının toplam harcamasını ve kategori bazlı dökümünü hesaplar.
     * Aylık harcama 10.000 TL'yi aşarsa yanıta uyarı mesajı eklenir.
     *
     * @param userId Analiz edilecek kullanıcının ID'si
     * @return Toplam harcama, kategori dökümü ve varsa uyarı
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

        log.info("Analiz tamamlandı. userId={}, toplam={} TL, kategori sayısı={}",
                userId, totalSpending, categoryBreakdown.size());

        return new AnalyticsSummaryDto(totalSpending, categoryBreakdown, warning);
    }
}
