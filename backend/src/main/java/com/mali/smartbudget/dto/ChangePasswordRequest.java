package com.mali.smartbudget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Mevcut şifre boş olamaz")
        String currentPassword,

        @NotBlank(message = "Yeni şifre boş olamaz")
        @Size(min = 6, message = "Yeni şifre en az 6 karakter olmalı")
        String newPassword,

        @NotBlank(message = "Şifre tekrarı boş olamaz")
        String confirmPassword
) {}
