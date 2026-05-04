package com.mali.smartbudget.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * PUT /api/v1/user/monthly-budget istek gövdesi.
 * monthlyBudget null gönderilebilir (bütçeyi sıfırlamak için).
 */
public record BudgetGoalRequest(
        @DecimalMin(value = "0", inclusive = false, message = "Bütçe sıfırdan büyük olmalıdır.")
        BigDecimal monthlyBudget
) {}
