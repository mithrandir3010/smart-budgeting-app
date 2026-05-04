package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.BudgetAlertDto;
import com.mali.smartbudget.dto.BudgetLimitRequest;
import com.mali.smartbudget.model.BudgetLimit;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.BudgetLimitService;
import com.mali.smartbudget.service.AnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/budget-limits")
@RequiredArgsConstructor
public class BudgetLimitController {

    private final BudgetLimitService budgetLimitService;
    private final AnalyticsService   analyticsService;

    /** Kullanıcının tüm bütçe limitlerini döner. */
    @GetMapping
    public ResponseEntity<List<BudgetLimit>> getAll(@AuthenticationPrincipal User user) {
        log.debug("Bütçe limitleri istendi. userId={}", user.getId());
        return ResponseEntity.ok(budgetLimitService.getByUser(user.getId()));
    }

    /**
     * Limit oluşturur veya günceller (upsert).
     * Aynı kullanıcı + kategori çifti için ikinci çağrı limitAmount'u günceller.
     */
    @PostMapping
    public ResponseEntity<BudgetLimit> upsert(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody BudgetLimitRequest request) {

        log.info("Bütçe limiti upsert isteği. userId={}, kategori={}, tutar={}",
                user.getId(), request.category(), request.limitAmount());
        BudgetLimit saved = budgetLimitService.upsert(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** Belirtilen limiti siler. Başkasının limitine erişim 404 döner. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        log.info("Bütçe limiti silme isteği. userId={}, limitId={}", user.getId(), id);
        budgetLimitService.delete(id, user.getId());
        log.info("Bütçe limiti silindi. userId={}, limitId={}", user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Kullanıcının mevcut harcamalarına göre tüm limitlerin uyarı durumlarını döner.
     * AnalyticsService'teki categoryBreakdown ile karşılaştırılır.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<BudgetAlertDto>> getAlerts(@AuthenticationPrincipal User user) {
        log.info("Bütçe uyarıları istendi. userId={}", user.getId());
        Map<String, java.math.BigDecimal> breakdown =
                analyticsService.getSummary(user.getId()).categoryBreakdown();
        List<BudgetAlertDto> alerts = budgetLimitService.computeAlerts(user.getId(), breakdown);
        log.info("Bütçe uyarıları hesaplandı. userId={}, uyarı sayısı={}", user.getId(), alerts.size());
        return ResponseEntity.ok(alerts);
    }
}
