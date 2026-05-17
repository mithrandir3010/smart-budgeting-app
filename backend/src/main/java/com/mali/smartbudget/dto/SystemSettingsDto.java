package com.mali.smartbudget.dto;

public record SystemSettingsDto(
        boolean maintenanceMode,
        String  announcement,
        String  disabledBanks
) {}
