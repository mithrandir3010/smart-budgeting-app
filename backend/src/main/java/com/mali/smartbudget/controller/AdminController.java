package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.*;
import com.mali.smartbudget.model.AuditLog;
import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.AuditLogRepository;
import com.mali.smartbudget.repository.StatementRepository;
import com.mali.smartbudget.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository      userRepository;
    private final StatementRepository statementRepository;
    private final AuditLogRepository  auditLogRepository;

    // ── GET /api/v1/admin/stats ───────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        long totalUsers       = userRepository.count();
        long activeUsers30d   = userRepository.countActiveUsersSince(LocalDateTime.now().minusDays(30));
        long totalStatements  = statementRepository.count();
        long processed        = statementRepository.countByStatus(StatementStatus.PROCESSED);
        double successRate    = totalStatements > 0 ? (processed * 100.0 / totalStatements) : 100.0;

        long newThisWeek      = userRepository.countUsersCreatedSince(LocalDateTime.now().minusDays(7));
        long newLastWeek      = userRepository.countUsersCreatedSince(LocalDateTime.now().minusDays(14))
                                - newThisWeek;

        return ResponseEntity.ok(new AdminStatsDto(
                totalUsers, activeUsers30d, totalStatements,
                Math.round(successRate * 10.0) / 10.0,
                newThisWeek, newLastWeek));
    }

    // ── GET /api/v1/admin/users ───────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDto>> getUsers(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(required = false)    String search) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.searchUsers(search, pageable);
        Page<AdminUserDto> dtos = users.map(u -> toAdminUserDto(u,
                statementRepository.countByUserId(u.getId())));
        return ResponseEntity.ok(dtos);
    }

    // ── GET /api/v1/admin/users/{id} ──────────────────────────────────────────
    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDto> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + id));
        return ResponseEntity.ok(toAdminUserDto(user, statementRepository.countByUserId(id)));
    }

    // ── GET /api/v1/admin/users/{id}/statements ───────────────────────────────
    @GetMapping("/users/{id}/statements")
    public ResponseEntity<List<AdminStatementDto>> getUserStatements(@PathVariable Long id) {
        List<Statement> statements = statementRepository.findByUserId(id);
        List<AdminStatementDto> dtos = statements.stream().map(s -> new AdminStatementDto(
                s.getId(), s.getFileName(), s.getUploadDate(),
                s.getStatus().name(), s.getBankName(),
                s.getPeriodStart(), s.getPeriodEnd()
        )).toList();
        return ResponseEntity.ok(dtos);
    }

    // ── PUT /api/v1/admin/users/{id}/status ───────────────────────────────────
    @PutMapping("/users/{id}/status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + id));

        boolean active = Boolean.TRUE.equals(body.get("active"));
        user.setActive(active);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message", active ? "Kullanıcı aktifleştirildi." : "Kullanıcı devre dışı bırakıldı.",
                "username", user.getUsername()));
    }

    // ── GET /api/v1/admin/audit ───────────────────────────────────────────────
    @GetMapping("/audit")
    public ResponseEntity<Page<AdminAuditDto>> getAuditLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        Page<AdminAuditDto> dtos = logs.map(l -> new AdminAuditDto(
                l.getId(), l.getEventType(), l.getUsername(), l.getUserId(),
                l.getIpAddress(), l.getDetails(), l.getCreatedAt()));
        return ResponseEntity.ok(dtos);
    }

    // ── GET /api/v1/admin/growth ──────────────────────────────────────────────
    @GetMapping("/growth")
    public ResponseEntity<List<DailyGrowthDto>> getUserGrowth() {
        List<Object[]> rows = userRepository.findDailyGrowthLast30Days();
        List<DailyGrowthDto> result = rows.stream()
                .map(r -> new DailyGrowthDto((String) r[0], ((Number) r[1]).longValue()))
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private AdminUserDto toAdminUserDto(User u, long statementCount) {
        return new AdminUserDto(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                u.getRole(), u.isEmailVerified(), u.isActive(),
                u.getLoginCount(), u.getLastLoginAt(), u.getCreatedAt(),
                statementCount);
    }
}
