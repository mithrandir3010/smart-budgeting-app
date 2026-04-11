package com.mali.smartbudget.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * GET /api/v1/analytics/summary yanıtı.
 *
 * @param totalSpending     Kullanıcının toplam harcaması
 * @param categoryBreakdown Kategori bazlı harcama dökümü  {"Market": 245.90, "Kira": 12000.00}
 * @param warning           Aylık limit aşıldıysa uyarı mesajı, aksi hâlde null
 * @param coachAdvice       Harcama hızına dayalı Serena'nın proaktif koçluk tavsiyesi
 * @param monthlyBudget     Aylık bütçe limiti (varsayılan 10.000 TL)
 * @param projectedSpending Mevcut harcama hızıyla hesaplanan ay sonu tahmini
 * @param dailyRate         Günlük ortalama harcama hızı
 */
public record AnalyticsSummaryDto(
        BigDecimal totalSpending,
        Map<String, BigDecimal> categoryBreakdown,
        String warning,
        String coachAdvice,
        BigDecimal monthlyBudget,
        BigDecimal projectedSpending,
        BigDecimal dailyRate
) {}
