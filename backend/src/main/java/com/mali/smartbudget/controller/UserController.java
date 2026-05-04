package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.BudgetGoalRequest;
import com.mali.smartbudget.dto.ChangePasswordRequest;
import com.mali.smartbudget.dto.UpdateProfileRequest;
import com.mali.smartbudget.dto.UserProfileDto;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(@AuthenticationPrincipal User currentUser) {
        log.info("Profil bilgisi istendi. userId={}, username={}", currentUser.getId(), currentUser.getUsername());
        return ResponseEntity.ok(userService.getCurrentUserProfile(currentUser));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("Profil güncelleme isteği. userId={}, yeni email={}", currentUser.getId(), request.email());
        UserProfileDto updated = userService.updateUserProfile(currentUser, request);
        log.info("Profil güncellendi. userId={}", currentUser.getId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("Şifre değişikliği isteği. userId={}, username={}", currentUser.getId(), currentUser.getUsername());
        userService.changePassword(currentUser, request);
        log.info("Şifre başarıyla değiştirildi. userId={}", currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/monthly-budget")
    public ResponseEntity<Void> updateMonthlyBudget(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody BudgetGoalRequest request) {
        log.info("Aylık bütçe hedefi güncellendi. userId={}, yeni limit={} TL",
                currentUser.getId(), request.monthlyBudget());
        currentUser.setMonthlyBudget(request.monthlyBudget());
        userService.saveUser(currentUser);
        return ResponseEntity.noContent().build();
    }
}
