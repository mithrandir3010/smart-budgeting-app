package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.PasswordResetToken;
import com.mali.smartbudget.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
}
