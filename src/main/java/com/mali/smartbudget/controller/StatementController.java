package com.mali.smartbudget.controller;

import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.service.ExtractionService;
import com.mali.smartbudget.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
public class StatementController {

    private final ExtractionService extractionService;
    private final TransactionService transactionService;

    /**
     * PDF ekstre dosyasını alır, LLM ile işlemlerini ayıklar ve veritabanına kaydeder.
     *
     * <pre>
     * POST /api/v1/statements/upload
     * Content-Type: multipart/form-data
     *
     * Parametreler:
     *   file   — PDF ekstre dosyası
     *   userId — Dosyanın sahibi kullanıcının ID'si
     * </pre>
     */
    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Dosya boş olamaz.");
        }

        log.info("Ekstre yükleme isteği alındı. userId={}, dosya={}", userId, file.getOriginalFilename());

        List<Transaction> saved;
        try {
            List<Transaction> transactions = extractionService.extractAndMap(file, userId);
            saved = transactionService.saveAllTransactions(transactions);
        } catch (IOException e) {
            log.error("PDF işlenirken hata oluştu: {}", e.getMessage(), e);
            return ResponseEntity.unprocessableEntity()
                    .body("PDF okunamadı: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JSON parse hatası: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity()
                    .body("İşlem verileri ayıklanamadı: " + e.getMessage());
        }

        String message = "Başarıyla işlendi. %d işlem veritabanına kaydedildi.".formatted(saved.size());
        log.info(message);
        return ResponseEntity.ok(message);
    }
}
