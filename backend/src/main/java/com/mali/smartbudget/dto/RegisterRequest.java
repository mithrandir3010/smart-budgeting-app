package com.mali.smartbudget.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Kullanıcı adı boş olamaz")
        @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalı")
        String username,

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 8, message = "Şifre en az 8 karakter olmalı")
        @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
            message = "Şifre en az bir büyük harf ve bir rakam içermelidir"
        )
        String password,

        @NotBlank(message = "Ad Soyad boş olamaz")
        String fullName
) {}
