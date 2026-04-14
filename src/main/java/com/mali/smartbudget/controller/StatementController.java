package com.mali.smartbudget.controller;

import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    private final StatementService statementService;

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

        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "ekstre.pdf";

        log.info("Ekstre yükleme isteği. userId={}, username={}, dosya='{}', boyut={} byte",
                currentUser.getId(), currentUser.getUsername(), fileName, file.getSize());

        String message = statementService.processUpload(file, currentUser.getId(), fileName);
        log.info("Yükleme tamamlandı. userId={}, sonuç='{}'", currentUser.getId(), message);

        return ResponseEntity.ok(message);
    }
}
