package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.service.AnalyticsService;
import com.mali.smartbudget.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<AnalyticsSummaryDto> getSummary(@RequestParam Long userId) {
        log.info("Analiz isteği alındı. userId={}", userId);
        return ResponseEntity.ok(analyticsService.getSummary(userId));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(@RequestParam Long userId) {
        log.info("İşlem listesi isteği alındı. userId={}", userId);
        List<TransactionDto> dtos = transactionService.getTransactionsByUser(userId)
                .stream()
                .map(t -> new TransactionDto(t.getDate(), t.getDescription(), t.getAmount(), t.getCategory(), t.getCurrency(), t.isSubscription()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<TransactionDto>> getSubscriptions(@RequestParam Long userId) {
        log.info("Abonelik listesi isteği alındı. userId={}", userId);
        List<TransactionDto> dtos = transactionService.getSubscriptionsByUser(userId)
                .stream()
                .map(t -> new TransactionDto(t.getDate(), t.getDescription(), t.getAmount(), t.getCategory(), t.getCurrency(), t.isSubscription()))
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
