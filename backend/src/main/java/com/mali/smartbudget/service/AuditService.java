package com.mali.smartbudget.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditService {

    public void loginSuccess(String username, String ip) {
        log.warn("AUDIT LOGIN_SUCCESS username={} ip={}", username, ip);
    }

    public void loginFailure(String username, String ip, int failedAttempts) {
        log.warn("AUDIT LOGIN_FAILURE username={} ip={} failedAttempts={}", username, ip, failedAttempts);
    }

    public void accountLocked(String username, String ip) {
        log.warn("AUDIT ACCOUNT_LOCKED username={} ip={}", username, ip);
    }

    public void passwordChanged(Long userId, String username) {
        log.warn("AUDIT PASSWORD_CHANGED userId={} username={}", userId, username);
    }

    public void profileUpdated(Long userId, String username) {
        log.warn("AUDIT PROFILE_UPDATED userId={} username={}", userId, username);
    }

    public void statementUploaded(Long userId, String filename) {
        log.warn("AUDIT STATEMENT_UPLOADED userId={} filename={}", userId, filename);
    }

    public void statementDeleted(Long userId) {
        log.warn("AUDIT STATEMENT_DELETED userId={}", userId);
    }

    public void disposableEmailBlocked(String email, String ip) {
        log.warn("AUDIT DISPOSABLE_EMAIL_BLOCKED email={} ip={}", email, ip);
    }
}
