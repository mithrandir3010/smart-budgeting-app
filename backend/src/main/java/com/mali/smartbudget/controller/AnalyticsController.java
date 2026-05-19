package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.dto.SubscriptionSummaryDto;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Transaction;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
                .map(t -> new TransactionDto(t.getDate(), t.getDescription(), t.getAmount(), t.getCategory(), t.getCurrency(), t.isSubscription(), t.isInstallment(), t.getCurrentInstallment(), t.getTotalInstallments(), t.getCategoryEnum()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionSummaryDto>> getSubscriptions(@AuthenticationPrincipal User currentUser) {
        log.info("Abonelik listesi isteği alındı. userId={}", currentUser.getId());

        List<Transaction> all = transactionService.getSubscriptionsByUser(currentUser.getId());

        // description bazlı gruplama (case-insensitive): en son ödenen tutar + kaç aydır tespit edildiği
        List<SubscriptionSummaryDto> summaries = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.getDescription().toLowerCase(java.util.Locale.ROOT)))
                .entrySet().stream()
                .map(e -> {
                    List<Transaction> group = e.getValue();
                    Transaction latest = group.stream()
                            .max(Comparator.comparing(Transaction::getDate))
                            .orElseThrow();
                    long distinctMonths = group.stream()
                            .map(t -> t.getDate().withDayOfMonth(1))
                            .distinct()
                            .count();
                    return new SubscriptionSummaryDto(
                            latest.getDescription(),
                            latest.getCategory(),
                            latest.getAmount(),
                            (int) distinctMonths);
                })
                .sorted(Comparator.comparing(SubscriptionSummaryDto::latestAmount).reversed())
                .toList();

        log.info("Abonelik özeti: {} tekil abonelik ({} toplam kayıt)", summaries.size(), all.size());
        return ResponseEntity.ok(summaries);
    }
}
