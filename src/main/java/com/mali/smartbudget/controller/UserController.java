package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.BudgetGoalRequest;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
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

    private final UserRepository userRepository;

    /**
     * Kullanıcının aylık bütçe hedefini günceller.
     *
     * <pre>
     * PUT /api/v1/user/monthly-budget
     * { "monthlyBudget": 10000.00 }
     * </pre>
     *
     * monthlyBudget null gönderilemez — minimum 0.01 TL olmalıdır.
     * Bütçeyi kaldırmak için gelecekte DELETE endpoint eklenebilir.
     */
    @PutMapping("/monthly-budget")
    public ResponseEntity<Void> updateMonthlyBudget(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody BudgetGoalRequest request) {

        currentUser.setMonthlyBudget(request.monthlyBudget());
        userRepository.save(currentUser);
        log.info("Aylık bütçe hedefi güncellendi. userId={}, yeni limit={} TL",
                currentUser.getId(), request.monthlyBudget());
        return ResponseEntity.noContent().build();
    }
}
