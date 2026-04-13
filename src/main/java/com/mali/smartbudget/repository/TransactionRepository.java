package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByStatementId(Long statementId);

    void deleteAllByUserId(Long userId);

    List<Transaction> findByUserIdAndIsSubscriptionTrue(Long userId);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.user.id = :userId GROUP BY t.category")
    List<Object[]> findCategoryTotals(@Param("userId") Long userId);
}
