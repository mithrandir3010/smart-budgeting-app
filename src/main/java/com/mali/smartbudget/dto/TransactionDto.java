package com.mali.smartbudget.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * LLM'den dönen ham JSON'ı karşılamak için kullanılan veri transfer nesnesi.
 * JPA entity'sinden (Transaction) ayrı tutuldu: entity'ye Jackson annotation eklememek için.
 */
public record TransactionDto(

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        String description,
        BigDecimal amount,
        String category,
        String currency,
        boolean isSubscription
) {}
