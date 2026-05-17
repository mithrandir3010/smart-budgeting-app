package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByUserId(Long userId);

    long countByUserId(Long userId);

    long countByStatus(StatementStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Statement s WHERE s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    List<Statement> findByUserIdAndStatus(Long userId, StatementStatus status);

    // ── Mükerrer kayıt engelleme sorguları ───────────────────────────────────

    /**
     * Aynı kullanıcıya ait, aynı SHA-256 checksuma sahip bir Statement var mı?
     *
     * <p>Spring Data JPA method name derivation ile türetilmiştir.
     * Null checksum değerleri bu sorguyla eşleşmez (SQL IS NOT NULL davranışı).
     */
    boolean existsByUserIdAndSha256Checksum(Long userId, String sha256Checksum);

    /**
     * Yeni ekstre dönemi [periodStart, periodEnd] mevcut herhangi bir
     * PROCESSED Statement ile örtüşüyor mu?
     *
     * <p>Örtüşme koşulu (iki interval [s1,e1] ve [s2,e2]):
     * <pre>s1 ≤ e2 AND e1 ≥ s2</pre>
     * Bu koşul "tamamen ayrık değil" anlamına gelir ve her türlü örtüşmeyi yakalar:
     * kısmi örtüşme, tam kapsama, biri diğerini içinde barındırma.
     *
     * <p>Sadece period_start ve period_end değerleri dolu olan kayıtlar karşılaştırılır.
     *
     * @param userId      Kullanıcı ID'si
     * @param periodStart Yeni ekstrenin en eski işlem tarihi
     * @param periodEnd   Yeni ekstrenin en yeni işlem tarihi
     * @return Çakışan Statement kaydı sayısı (0 → çakışma yok, >0 → çakışma var)
     */
    @Query("""
            SELECT COUNT(s) FROM Statement s
            WHERE s.user.id      = :userId
              AND s.status       = com.mali.smartbudget.model.StatementStatus.PROCESSED
              AND s.periodStart  IS NOT NULL
              AND s.periodEnd    IS NOT NULL
              AND s.periodStart  <= :periodEnd
              AND s.periodEnd    >= :periodStart
              AND (:bankName IS NULL OR s.bankName IS NULL OR s.bankName = :bankName)
            """)
    long countOverlappingPeriods(@Param("userId") Long userId,
                                 @Param("periodStart") LocalDate periodStart,
                                 @Param("periodEnd") LocalDate periodEnd,
                                 @Param("bankName") String bankName);
}
