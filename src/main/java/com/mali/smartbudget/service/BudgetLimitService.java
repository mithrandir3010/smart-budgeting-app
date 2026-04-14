package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.BudgetAlertDto;
import com.mali.smartbudget.dto.BudgetLimitRequest;
import com.mali.smartbudget.model.AlertStatus;
import com.mali.smartbudget.model.BudgetLimit;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.BudgetLimitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetLimitService {

    /** ≥ %90 → CRITICAL */
    private static final double CRITICAL_THRESHOLD = 90.0;
    /** ≥ %70 → WARNING */
    private static final double WARNING_THRESHOLD  = 70.0;

    private final BudgetLimitRepository budgetLimitRepository;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BudgetLimit> getByUser(Long userId) {
        return budgetLimitRepository.findByUserId(userId);
    }

    /**
     * Upsert: aynı kategori için limit zaten varsa günceller, yoksa oluşturur.
     */
    @Transactional
    public BudgetLimit upsert(User user, BudgetLimitRequest request) {
        BudgetLimit limit = budgetLimitRepository
                .findByUserIdAndCategory(user.getId(), request.category())
                .orElseGet(() -> BudgetLimit.builder()
                        .user(user)
                        .category(request.category())
                        .build());

        limit.setLimitAmount(request.limitAmount());
        BudgetLimit saved = budgetLimitRepository.save(limit);

        log.info("Bütçe limiti upsert edildi: userId={}, kategori={}, limit={}",
                user.getId(), request.category(), request.limitAmount());
        return saved;
    }

    /**
     * Sadece kendi limitini silebilir. Başkasına ait ID gelirse 404 fırlatır.
     */
    @Transactional
    public void delete(Long limitId, Long userId) {
        if (!budgetLimitRepository.existsByIdAndUserId(limitId, userId)) {
            throw new EntityNotFoundException("Bütçe limiti bulunamadı: id=" + limitId);
        }
        budgetLimitRepository.deleteById(limitId);
        log.info("Bütçe limiti silindi: limitId={}, userId={}", limitId, userId);
    }

    // ── Alert Hesaplama ───────────────────────────────────────────────────────

    /**
     * Kullanıcının tanımladığı tüm kategorileri gerçek harcamalarıyla karşılaştırıp
     * uyarı durumlarını hesaplar. Limit tanımlanmamış kategoriler dahil edilmez.
     *
     * @param userId            Kullanıcı ID'si
     * @param categorySpending  Kategoriden toplam harcama map'i
     * @return Her kategori için BudgetAlertDto listesi (OK dahil)
     */
    @Transactional(readOnly = true)
    public List<BudgetAlertDto> computeAlerts(Long userId,
                                               Map<String, BigDecimal> categorySpending) {
        List<BudgetLimit> limits = budgetLimitRepository.findByUserId(userId);

        return limits.stream().map(limit -> {
            BigDecimal spent = categorySpending
                    .getOrDefault(limit.getCategory(), BigDecimal.ZERO);

            double pct = limit.getLimitAmount().compareTo(BigDecimal.ZERO) == 0
                    ? 0.0
                    : spent.divide(limit.getLimitAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

            AlertStatus status;
            if (pct >= CRITICAL_THRESHOLD) {
                status = AlertStatus.CRITICAL;
            } else if (pct >= WARNING_THRESHOLD) {
                status = AlertStatus.WARNING;
            } else {
                status = AlertStatus.OK;
            }

            return new BudgetAlertDto(
                    limit.getId(),
                    limit.getCategory(),
                    spent.setScale(2, RoundingMode.HALF_UP),
                    limit.getLimitAmount(),
                    Math.round(pct * 10.0) / 10.0,
                    status
            );
        }).toList();
    }
}
