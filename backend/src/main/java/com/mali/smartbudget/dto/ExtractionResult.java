package com.mali.smartbudget.dto;

import java.util.List;

/**
 * ExtractionService.extractAll() dönüş tipi.
 * DTO listesinin yanı sıra tespit edilen banka adını da taşır.
 */
public record ExtractionResult(
        List<TransactionDto> dtos,
        String bankName,
        String headerText,
        String maskedCardNo,
        java.time.LocalDate statementCutDate) {}
