package com.mali.smartbudget.dto;

public record AdminStatsDto(
        long totalUsers,
        long activeUsersLast30Days,
        long totalStatements,
        double successRate,
        long newUsersThisWeek,
        long newUsersLastWeek
) {}
