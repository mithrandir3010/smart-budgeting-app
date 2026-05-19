package com.mali.smartbudget.dto;

import java.math.BigDecimal;

public record SubscriptionSummaryDto(
        String description,
        String category,
        BigDecimal latestAmount,
        int monthCount) {}
