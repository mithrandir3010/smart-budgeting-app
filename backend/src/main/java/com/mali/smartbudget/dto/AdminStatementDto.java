package com.mali.smartbudget.dto;

import java.time.LocalDate;

public record AdminStatementDto(
        Long id,
        String fileName,
        LocalDate uploadDate,
        String status,
        String bankName,
        LocalDate periodStart,
        LocalDate periodEnd
) {}
