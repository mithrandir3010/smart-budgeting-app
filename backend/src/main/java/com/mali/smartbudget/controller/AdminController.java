package com.mali.smartbudget.controller;

import com.mali.smartbudget.dto.*;
import com.mali.smartbudget.model.AuditLog;
import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.AuditLogRepository;
import com.mali.smartbudget.repository.StatementRepository;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.service.SystemSettingService;
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

    private final UserRepository       userRepository;
    private final StatementRepository  statementRepository;
    private final AuditLogRepository   auditLogRepository;
    private final SystemSettingService systemSettingService;

    // ── GET /stats ────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getStats() {
        long totalUsers      = userRepository.count();
        long activeUsers30d  = userRepository.countActiveUsersSince(LocalDateTime.now().minusDays(30));
        long totalStatements = statementRepository.count();
        long processed       = statementRepository.countByStatus(StatementStatus.PROCESSED);
        double successRate   = totalStatements > 0 ? (processed * 100.0 / totalStatements) : 100.0;

        long newThisWeek = userRepository.countUsersCreatedSince(LocalDateTime.now().minusDays(7));
        long newLastWeek = userRepository.countUsersCreatedSince(LocalDateTime.now().minusDays(14)) - newThisWeek;

        return ResponseEntity.ok(new AdminStatsDto(
                totalUsers, activeUsers30d, totalStatements,
                Math.round(successRate * 10.0) / 10.0,
                newThisWeek, newLastWeek));
    }

    // ── GET /users ────────────────────────────────────────────────────────────
    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserDto>> getUsers(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(required = false)    String search) {

        PageRequest pageable   = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String      searchParam = (search == null || search.isBlank()) ? null : search;
        Page<User>  users      = userRepository.searchUsers(searchParam, pageable);
        return ResponseEntity.ok(users.map(u ->
                toAdminUserDto(u, statementRepository.countByUserId(u.getId()))));
    }

    // ── GET /users/{id} ───────────────────────────────────────────────────────
    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDto> getUserById(@PathVariable Long id) {
        User user = findUser(id);
        return ResponseEntity.ok(toAdminUserDto(user, statementRepository.countByUserId(id)));
    }

    // ── GET /users/{id}/statements ────────────────────────────────────────────
    @GetMapping("/users/{id}/statements")
    public ResponseEntity<List<AdminStatementDto>> getUserStatements(@PathVariable Long id) {
        return ResponseEntity.ok(statementRepository.findByUserId(id).stream()
                .map(s -> new AdminStatementDto(
                        s.getId(), s.getFileName(), s.getUploadDate(),
                        s.getStatus().name(), s.getBankName(),
                        s.getPeriodStart(), s.getPeriodEnd()))
                .toList());
    }

    // ── PUT /users/{id}/status ────────────────────────────────────────────────
    @PutMapping("/users/{id}/status")
    public ResponseEntity<Map<String, String>> toggleUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {

        User user = findUser(id);
        boolean active = Boolean.TRUE.equals(body.get("active"));
        user.setActive(active);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "message",  active ? "Kullanıcı aktifleştirildi." : "Kullanıcı devre dışı bırakıldı.",
                "username", user.getUsername()));
    }

    // ── POST /users/bulk-status ───────────────────────────────────────────────
    @PostMapping("/users/bulk-status")
    public ResponseEntity<Map<String, Object>> bulkToggleStatus(@RequestBody BulkStatusRequest req) {
        if (req.userIds() == null || req.userIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userIds boş olamaz."));
        }
        List<User> users = userRepository.findAllById(req.userIds());
        users.forEach(u -> {
            if (!"ROLE_ADMIN".equals(u.getRole())) u.setActive(req.active());
        });
        userRepository.saveAll(users);
        return ResponseEntity.ok(Map.of(
                "updated", users.size(),
                "active",  req.active()));
    }

    // ── GET /audit ────────────────────────────────────────────────────────────
    @GetMapping("/audit")
    public ResponseEntity<Page<AdminAuditDto>> getAuditLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(l -> new AdminAuditDto(
                        l.getId(), l.getEventType(), l.getUsername(), l.getUserId(),
                        l.getIpAddress(), l.getDetails(), l.getCreatedAt())));
    }

    // ── GET /growth ───────────────────────────────────────────────────────────
    @GetMapping("/growth")
    public ResponseEntity<List<DailyGrowthDto>> getUserGrowth() {
        return ResponseEntity.ok(userRepository.findDailyGrowthLast30Days().stream()
                .map(r -> new DailyGrowthDto((String) r[0], ((Number) r[1]).longValue()))
                .toList());
    }

    // ── GET /bank-stats ───────────────────────────────────────────────────────
    @GetMapping("/bank-stats")
    public ResponseEntity<List<BankStatsDto>> getBankStats() {
        List<Object[]> rows  = statementRepository.findBankDistribution();
        long           total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();
        return ResponseEntity.ok(rows.stream()
                .map(r -> {
                    long cnt = ((Number) r[1]).longValue();
                    double pct = total > 0 ? Math.round((cnt * 1000.0 / total)) / 10.0 : 0.0;
                    return new BankStatsDto((String) r[0], cnt, pct);
                })
                .toList());
    }

    // ── GET /funnel ───────────────────────────────────────────────────────────
    @GetMapping("/funnel")
    public ResponseEntity<FunnelDto> getFunnel() {
        long registered   = userRepository.count();
        long verified     = userRepository.countByEmailVerifiedTrue();
        long firstUpload  = userRepository.countUsersWithAnyStatement();
        return ResponseEntity.ok(new FunnelDto(registered, verified, firstUpload));
    }

    // ── GET /silent-failures ──────────────────────────────────────────────────
    @GetMapping("/silent-failures")
    public ResponseEntity<List<SilentFailureDto>> getSilentFailures() {
        return ResponseEntity.ok(statementRepository.findSilentFailures().stream()
                .map(r -> new SilentFailureDto(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        (String) r[3],
                        r[4] != null ? r[4].toString() : null,
                        (String) r[5]))
                .toList());
    }

    // ── GET /failed-statements ────────────────────────────────────────────────
    @GetMapping("/failed-statements")
    public ResponseEntity<List<SilentFailureDto>> getFailedStatements() {
        return ResponseEntity.ok(statementRepository.findFailedStatements().stream()
                .map(r -> new SilentFailureDto(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        (String) r[3],
                        r[4] != null ? r[4].toString() : null,
                        (String) r[5]))
                .toList());
    }

    // ── GET /settings ─────────────────────────────────────────────────────────
    @GetMapping("/settings")
    public ResponseEntity<SystemSettingsDto> getSettings() {
        return ResponseEntity.ok(new SystemSettingsDto(
                systemSettingService.isMaintenanceMode(),
                systemSettingService.getAnnouncement(),
                systemSettingService.get("disabled_banks", "")));
    }

    // ── PUT /settings ─────────────────────────────────────────────────────────
    @PutMapping("/settings")
    public ResponseEntity<Map<String, String>> updateSettings(@RequestBody SystemSettingsDto dto) {
        systemSettingService.set("maintenance_mode", dto.maintenanceMode() ? "true" : "false");
        systemSettingService.set("announcement",     dto.announcement() != null ? dto.announcement() : "");
        systemSettingService.set("disabled_banks",   dto.disabledBanks() != null ? dto.disabledBanks() : "");
        return ResponseEntity.ok(Map.of("message", "Ayarlar kaydedildi."));
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı: " + id));
    }

    private AdminUserDto toAdminUserDto(User u, long statementCount) {
        return new AdminUserDto(
                u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                u.getRole(), u.isEmailVerified(), u.isActive(),
                u.getLoginCount(), u.getLastLoginAt(), u.getCreatedAt(),
                statementCount);
    }
}
