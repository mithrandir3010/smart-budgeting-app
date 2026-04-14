package com.mali.smartbudget.dto;

public record AuthResponse(
        String token,
        String username,
        String email,
        String fullName
) {}
