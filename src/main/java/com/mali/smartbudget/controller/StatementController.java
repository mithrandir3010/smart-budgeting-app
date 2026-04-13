package com.mali.smartbudget.controller;

import com.mali.smartbudget.service.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF ekstre yükleme endpoint'i.
 *
 * <p>Tüm iş mantığı {@link StatementService#processUpload} içindedir.
 * Controller yalnızca HTTP katmanını yönetir:
 * <ul>
 *   <li>Boş dosya kontrolü (erken dönüş, 400)</li>
 *   <li>processUpload() çağrısı</li>
 *   <li>Exception'lar → {@code GlobalExceptionHandler} tarafından yakalanır</li>
 * </ul>
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
     * Content-Type: multipart/form-data
     *   file   — PDF dosyası (max 2MB)
     *   userId — Kullanıcı ID'si
     * </pre>
     *
     * <p>Olası HTTP yanıt kodları:
     * <ul>
     *   <li>200 OK — Başarılı işlem, kaydedilen işlem sayısı ve dönemi döner</li>
     *   <li>400 Bad Request — Dosya boş veya userId eksik</li>
     *   <li>404 Not Found — Kullanıcı bulunamadı</li>
     *   <li>409 Conflict — Aynı dosya veya aynı dönem daha önce yüklendi</li>
     *   <li>422 Unprocessable Entity — PDF okunamadı veya LLM geçersiz JSON döndürdü</li>
     * </ul>
     */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Dosya boş olamaz.");
        }

        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "ekstre.pdf";

        log.info("Ekstre yükleme isteği. userId={}, dosya='{}', boyut={} byte",
                userId, fileName, file.getSize());

        String message = statementService.processUpload(file, userId, fileName);
        log.info("Yükleme tamamlandı. userId={}, sonuç='{}'", userId, message);

        return ResponseEntity.ok(message);
    }
}
