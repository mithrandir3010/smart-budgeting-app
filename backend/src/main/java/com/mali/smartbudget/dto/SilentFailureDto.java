package com.mali.smartbudget.dto;

public record SilentFailureDto(
        Long   statementId,
        String fileName,
        Long   userId,
        String username,
        String uploadDate,
        String bankName
) {}
