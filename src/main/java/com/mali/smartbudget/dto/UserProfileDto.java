package com.mali.smartbudget.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserProfileDto(
        Long id,
        String username,
        String email,
        String fullName,
        BigDecimal monthlyBudget,
        LocalDateTime createdAt
) {}
