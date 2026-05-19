package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByStatementId(Long statementId);

    /**
     * Tek bir bulk DELETE sorgusu — Spring Data derived delete'in aksine
     * her entity için SELECT+DELETE yapmaz. clearAutomatically=true
     * Hibernate birinci seviye önbelleğini temizler; flushAutomatically=true
     * bekleyen INSERT/UPDATE'leri önceden flush eder.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Transaction t WHERE t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    List<Transaction> findByUserIdAndIsSubscriptionTrue(Long userId);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId GROUP BY t.category")
    List<Object[]> findCategoryTotals(@Param("userId") Long userId);

    @Query("SELECT MIN(t.date), MAX(t.date) FROM Transaction t WHERE t.user.id = :userId")
    List<Object[]> findDateRange(@Param("userId") Long userId);

    @Query(value = "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t WHERE t.user_id = :userId AND EXTRACT(YEAR FROM t.date) = :year AND EXTRACT(MONTH FROM t.date) = :month", nativeQuery = true)
    BigDecimal findMonthlyTotal(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

    @Query(value = "SELECT COUNT(DISTINCT DATE_TRUNC('month', date)) FROM transactions WHERE user_id = :userId", nativeQuery = true)
    long countDistinctMonths(@Param("userId") Long userId);
}
