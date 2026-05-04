package com.mali.smartbudget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * POST /api/v1/budget-limits isteğinin gövdesi.
 * Aynı kategori için tekrar gönderilirse mevcut limit güncellenir (upsert).
 */
public record BudgetLimitRequest(

        @NotBlank(message = "Kategori boş olamaz")
        String category,

        @NotNull(message = "Limit tutarı zorunludur")
        @DecimalMin(value = "1.0", message = "Limit en az 1 TL olmalıdır")
        BigDecimal limitAmount
) {}
