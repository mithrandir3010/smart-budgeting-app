package com.mali.smartbudget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementInfoDto(
        Long id,
        String fileName,
        String bankName,
        LocalDate statementCutDate,
        LocalDate periodEnd,
        LocalDate uploadDate,
        BigDecimal totalAmount
) {}
