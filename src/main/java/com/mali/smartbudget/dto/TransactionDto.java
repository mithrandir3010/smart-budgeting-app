package com.mali.smartbudget.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * LLM'den dönen ham JSON'ı karşılamak için kullanılan veri transfer nesnesi.
 * JPA entity'sinden (Transaction) ayrı tutuldu: entity'ye Jackson annotation eklememek için.
 *
 * NOT: boolean is-prefix alanları için @JsonProperty zorunludur.
 * Java record'larda Jackson, isSubscription() accessor'ını "subscription" olarak serialize eder
 * (is-prefix stripping). @JsonProperty ile tam alan adı korunur.
 */
public record TransactionDto(

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
        Integer totalInstallments
) {}
