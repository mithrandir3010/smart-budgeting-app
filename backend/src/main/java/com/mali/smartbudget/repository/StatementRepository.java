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
    @Query(value = """
            SELECT COALESCE(bank_name, 'UNKNOWN') AS bank, COUNT(*) AS cnt
            FROM statements
            GROUP BY bank
            ORDER BY cnt DESC
            """, nativeQuery = true)
    java.util.List<Object[]> findBankDistribution();

    @Query(value = """
            SELECT s.id, s.file_name, s.user_id, u.username, s.upload_date, s.bank_name
            FROM statements s
            JOIN users u ON u.id = s.user_id
            WHERE s.status = 'PROCESSED'
              AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.statement_id = s.id)
            ORDER BY s.upload_date DESC
            LIMIT 100
            """, nativeQuery = true)
    java.util.List<Object[]> findSilentFailures();

    @Query(value = """
            SELECT s.id, s.file_name, s.user_id, u.username, s.upload_date, s.bank_name
            FROM statements s
            JOIN users u ON u.id = s.user_id
            WHERE s.status = 'FAILED'
            ORDER BY s.upload_date DESC
            LIMIT 100
            """, nativeQuery = true)
    java.util.List<Object[]> findFailedStatements();

    /**
     * Aynı kart parmak izi (banka + maskeli kart no + kesim tarihi) ile
     * daha önce yüklenmiş bir PROCESSED ekstre var mı?
     *
     * <p>Üç alan da non-null olduğunda çağrılır. Bu kontrol dönem aralığı
     * karşılaştırmasının yerine geçer: aynı kart + aynı kesim tarihi = aynı ekstre.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM Statement s
            WHERE s.user.id           = :userId
              AND s.status            = com.mali.smartbudget.model.StatementStatus.PROCESSED
              AND s.bankName          = :bankName
              AND s.maskedCardNo      = :maskedCardNo
              AND s.statementCutDate  = :statementCutDate
            """)
    boolean existsByCardFingerprint(@Param("userId") Long userId,
                                    @Param("bankName") String bankName,
                                    @Param("maskedCardNo") String maskedCardNo,
                                    @Param("statementCutDate") LocalDate statementCutDate);
}
