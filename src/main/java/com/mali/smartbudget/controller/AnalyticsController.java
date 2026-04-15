package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.AnalyticsService;
import com.mali.smartbudget.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TransactionService transactionService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary(@AuthenticationPrincipal User currentUser) {
        log.info("Analiz isteği alındı. userId={}, username={}", currentUser.getId(), currentUser.getUsername());
        return ResponseEntity.ok(analyticsService.getSummary(currentUser.getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(@AuthenticationPrincipal User currentUser) {
        log.info("İşlem listesi isteği alındı. userId={}", currentUser.getId());
        List<TransactionDto> dtos = transactionService.getTransactionsByUser(currentUser.getId())
                .stream()
                .map(t -> new TransactionDto(t.getDate(), t.getDescription(), t.getAmount(), t.getCategory(), t.getCurrency(), t.isSubscription(), t.isInstallment(), t.getCurrentInstallment(), t.getTotalInstallments()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<TransactionDto>> getSubscriptions(@AuthenticationPrincipal User currentUser) {
        log.info("Abonelik listesi isteği alındı. userId={}", currentUser.getId());
        List<TransactionDto> dtos = transactionService.getSubscriptionsByUser(currentUser.getId())
                .stream()
                .map(t -> new TransactionDto(t.getDate(), t.getDescription(), t.getAmount(), t.getCategory(), t.getCurrency(), t.isSubscription(), t.isInstallment(), t.getCurrentInstallment(), t.getTotalInstallments()))
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
