package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.AnalyticsSummaryDto;
import com.mali.smartbudget.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Kullanıcının harcama özetini döner.
     *
     * <pre>
     * GET /api/v1/analytics/summary?userId=1
     *
     * Yanıt:
     * {
     *   "totalSpending": 12335.40,
     *   "categoryBreakdown": {
     *     "Market": 245.90,
     *     "Kafe": 89.50,
     *     "Kira": 12000.00
     *   },
     *   "warning": "Dikkat: Aylık harcamanız 12335.40 TL ile 10.000 TL limitini aştı!"
     * }
     * </pre>
     */
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getSummary(@RequestParam Long userId) {
        log.info("Analiz isteği alındı. userId={}", userId);
        AnalyticsSummaryDto summary = analyticsService.getSummary(userId);
        return ResponseEntity.ok(summary);
    }
}
