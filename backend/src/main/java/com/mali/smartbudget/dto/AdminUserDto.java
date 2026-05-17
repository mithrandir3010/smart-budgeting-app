package com.mali.smartbudget.dto;

import java.time.LocalDateTime;

public record AdminUserDto(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        boolean emailVerified,
        boolean active,
        int loginCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        long statementCount
) {}
