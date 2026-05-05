package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.EmailVerificationToken;
import com.mali.smartbudget.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByUser(User user);
}
