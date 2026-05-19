package com.mali.smartbudget.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mali.smartbudget.model.Category;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponseDto(

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        String description,
        BigDecimal amount,
        String category,
        String currency,

        @JsonProperty("isSubscription")
        boolean isSubscription,

        @JsonProperty("isInstallment")
        boolean isInstallment,

        Integer currentInstallment,
        Integer totalInstallments,
        Category categoryEnum,
        Long statementId
) {}
