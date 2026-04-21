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
        return ResponseEntity.ok(userService.getCurrentUserProfile(currentUser));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDto> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(currentUser, request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser, request);
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
