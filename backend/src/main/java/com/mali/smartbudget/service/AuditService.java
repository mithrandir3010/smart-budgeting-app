package com.mali.smartbudget.service;

import com.mali.smartbudget.model.AuditLog;
import com.mali.smartbudget.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // REQUIRES_NEW: audit kaydı ana transaction'ın rollback'inden etkilenmemeli
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginSuccess(String username, String ip) {
        log.warn("AUDIT LOGIN_SUCCESS username={} ip={}", username, ip);
        persist("LOGIN_SUCCESS", username, null, ip, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginFailure(String username, String ip, int failedAttempts) {
        log.warn("AUDIT LOGIN_FAILURE username={} ip={} failedAttempts={}", username, ip, failedAttempts);
        persist("LOGIN_FAILURE", username, null, ip, "failedAttempts=" + failedAttempts);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accountLocked(String username, String ip) {
        log.warn("AUDIT ACCOUNT_LOCKED username={} ip={}", username, ip);
        persist("ACCOUNT_LOCKED", username, null, ip, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void passwordChanged(Long userId, String username) {
        log.warn("AUDIT PASSWORD_CHANGED userId={} username={}", userId, username);
        persist("PASSWORD_CHANGED", username, userId, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void profileUpdated(Long userId, String username) {
        log.warn("AUDIT PROFILE_UPDATED userId={} username={}", userId, username);
        persist("PROFILE_UPDATED", username, userId, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void statementUploaded(Long userId, String filename) {
        log.warn("AUDIT STATEMENT_UPLOADED userId={} filename={}", userId, filename);
        persist("STATEMENT_UPLOADED", null, userId, null, "filename=" + filename);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void statementDeleted(Long userId) {
        log.warn("AUDIT STATEMENT_DELETED userId={}", userId);
        persist("STATEMENT_DELETED", null, userId, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disposableEmailBlocked(String email, String ip) {
        log.warn("AUDIT DISPOSABLE_EMAIL_BLOCKED email={} ip={}", email, ip);
        persist("DISPOSABLE_EMAIL_BLOCKED", null, null, ip, "email=" + email);
    }

    private void persist(String eventType, String username, Long userId, String ip, String details) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .eventType(eventType)
                    .username(username)
                    .userId(userId)
                    .ipAddress(ip)
                    .details(details)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.error("Audit log DB write failed for event={}: {}", eventType, e.getMessage());
        }
    }
}
