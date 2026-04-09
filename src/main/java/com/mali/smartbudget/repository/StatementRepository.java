package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByUserId(Long userId);

    List<Statement> findByUserIdAndStatus(Long userId, StatementStatus status);
}
