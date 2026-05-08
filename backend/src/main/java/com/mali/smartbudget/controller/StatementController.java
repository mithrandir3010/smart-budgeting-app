package com.mali.smartbudget.controller;

import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.RateLimitingService;
import com.mali.smartbudget.service.StatementService;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.text.Normalizer;

/**
 * PDF ekstre yükleme endpoint'i.
 *
 * <p>Kullanıcı kimliği JWT token'dan (@AuthenticationPrincipal) alınır —
 * artık request parametresi olarak gönderilmez.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementController {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final StatementService    statementService;
    private final RateLimitingService rateLimitingService;

    /**
     * PDF ekstre yükler, mükerrerlik kontrolü yapar ve harcamaları kaydeder.
     *
     * <pre>
     * POST /api/v1/statements/upload
     * Authorization: Bearer &lt;jwt&gt;
     * Content-Type: multipart/form-data
     *   file — PDF dosyası (max 2MB)
     * </pre>
     */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User currentUser) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Dosya boş olamaz.");
        }

        // ── Per-user upload rate limit ────────────────────────────────────────
        ConsumptionProbe probe = rateLimitingService.tryConsumeUpload(
                String.valueOf(currentUser.getId()));
        if (!probe.isConsumed()) {
            long retryAfter = (probe.getNanosToWaitForRefill() / 1_000_000_000L) + 1;
            log.warn("Upload rate limit aşıldı. userId={}", currentUser.getId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body("Saatlik yükleme limitine ulaştınız. Lütfen " + retryAfter + " saniye sonra tekrar deneyin.");
        }

        // ── MIME type kontrolü ────────────────────────────────────────────────
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            log.warn("Geçersiz MIME type. userId={}, contentType={}", currentUser.getId(), contentType);
            return ResponseEntity.badRequest().body("Yalnızca PDF dosyaları kabul edilmektedir.");
        }

        // ── Magic bytes kontrolü (%PDF) ───────────────────────────────────────
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            if (is.read(header) < 4 || !isPdf(header)) {
                log.warn("Geçersiz PDF magic bytes. userId={}", currentUser.getId());
                return ResponseEntity.badRequest().body("Yalnızca PDF dosyaları kabul edilmektedir.");
            }
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());

        log.info("Ekstre yükleme isteği. userId={}, username={}, dosya='{}', boyut={} byte",
                currentUser.getId(), currentUser.getUsername(), fileName, file.getSize());

        String message = statementService.processUpload(file, currentUser.getId(), fileName);
        log.info("Yükleme tamamlandı. userId={}, sonuç='{}'", currentUser.getId(), message);

        return ResponseEntity.ok(message);
    }

    /**
     * Kullanıcıya ait tüm işlem ve ekstre kayıtlarını siler.
     *
     * <pre>
     * DELETE /api/v1/statements/all
     * Authorization: Bearer &lt;jwt&gt;
     * </pre>
     */
    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAll(@AuthenticationPrincipal User currentUser) {
        log.info("Tüm veri silme isteği. userId={}, username={}",
                currentUser.getId(), currentUser.getUsername());
        statementService.deleteAllData(currentUser.getId());
        return ResponseEntity.ok("Tüm veriler silindi.");
    }

    private boolean isPdf(byte[] header) {
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (header[i] != PDF_MAGIC[i]) return false;
        }
        return true;
    }

    /**
     * Dosya adını güvenli hale getirir:
     * path traversal, unicode homoglyph ve tehlikeli karakter saldırılarını engeller.
     */
    private String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "ekstre.pdf";
        // Path traversal: sadece dosya adını al (../../etc/passwd.pdf → passwd.pdf)
        String name = Paths.get(raw).getFileName().toString();
        // Unicode normalizasyonu (homoglyph saldırıları)
        name = Normalizer.normalize(name, Normalizer.Form.NFKC);
        // Sadece güvenli karakterlere izin ver
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        // .pdf uzantısını garantile
        if (!name.toLowerCase().endsWith(".pdf")) name = name + ".pdf";
        // Maksimum 100 karakter
        if (name.length() > 100) name = name.substring(0, 96) + ".pdf";
        return name;
    }
}
