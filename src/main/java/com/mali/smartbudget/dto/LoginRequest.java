package com.mali.smartbudget.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Kullanıcı adı boş olamaz")
        String username,

        @NotBlank(message = "Şifre boş olamaz")
        String password
) {}
