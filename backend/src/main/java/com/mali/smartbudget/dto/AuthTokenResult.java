package com.mali.smartbudget.dto;

public record AuthTokenResult(
        String accessToken,
        String refreshToken,
        AuthResponse userInfo
) {}
