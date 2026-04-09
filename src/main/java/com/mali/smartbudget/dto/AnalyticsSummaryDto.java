package com.mali.smartbudget.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * GET /api/v1/analytics/summary yanıtı.
 *
 * @param totalSpending     Kullanıcının toplam harcaması
 * @param categoryBreakdown Kategori bazlı harcama dökümü  {"Market": 245.90, "Kira": 12000.00}
 * @param warning           Aylık limit (10.000 TL) aşıldıysa uyarı mesajı, aksi hâlde null
 */
public record AnalyticsSummaryDto(
        BigDecimal totalSpending,
        Map<String, BigDecimal> categoryBreakdown,
        String warning
) {}
