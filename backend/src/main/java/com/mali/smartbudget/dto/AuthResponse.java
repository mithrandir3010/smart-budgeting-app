package com.mali.smartbudget.dto;

public record AuthResponse(
        String username,
        String email,
        String fullName
) {}
