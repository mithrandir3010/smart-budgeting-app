package com.mali.smartbudget.dto;

public record AuthTokenResult(
        String token,
        AuthResponse userInfo
) {}
