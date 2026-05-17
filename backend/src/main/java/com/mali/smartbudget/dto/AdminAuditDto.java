package com.mali.smartbudget.dto;

import java.time.Instant;

public record AdminAuditDto(
        Long id,
        String eventType,
        String username,
        Long userId,
        String ipAddress,
        String details,
        Instant createdAt
) {}
