package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.BudgetLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetLimitRepository extends JpaRepository<BudgetLimit, Long> {

    List<BudgetLimit> findByUserId(Long userId);

    Optional<BudgetLimit> findByUserIdAndCategory(Long userId, String category);

    boolean existsByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);
}
