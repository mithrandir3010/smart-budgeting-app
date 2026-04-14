package com.mali.smartbudget.dto;

import com.mali.smartbudget.model.AlertStatus;

import java.math.BigDecimal;

/**
 * Tek bir kategori için anlık bütçe uyarısı.
 *
 * @param limitId         BudgetLimit entity ID'si (frontend'de silme işlemi için)
 * @param category        Harcama kategorisi
 * @param spent           Kategoride gerçekleşen toplam harcama
 * @param limitAmount     Kullanıcının tanımladığı limit
 * @param percentageUsed  spent / limitAmount * 100 (yüzde)
 * @param status          OK | WARNING | CRITICAL
 */
public record BudgetAlertDto(
        Long limitId,
        String category,
        BigDecimal spent,
        BigDecimal limitAmount,
        double percentageUsed,
        AlertStatus status
) {}
