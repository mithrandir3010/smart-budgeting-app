package com.mali.smartbudget.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Ad Soyad boş olamaz")
        @Size(max = 100, message = "Ad Soyad en fazla 100 karakter olabilir")
        String fullName,

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email
) {}
