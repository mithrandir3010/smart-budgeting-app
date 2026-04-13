package com.mali.smartbudget.controller;

import com.mali.smartbudget.exception.DuplicateStatementException;
import com.mali.smartbudget.service.StatementService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatementController MockMvc testleri.
 *
 * <p>Controller artık ince bir katman: tüm iş mantığı {@link StatementService}'te.
 * Bu nedenle {@code StatementService} mock'lanır; {@code ExtractionService} değil.
 *
 * <p>Exception → HTTP kodu eşleşmesi {@code GlobalExceptionHandler} tarafından yönetilir:
 * <ul>
 *   <li>DuplicateStatementException → 409 Conflict</li>
 *   <li>EntityNotFoundException     → 404 Not Found</li>
 *   <li>IllegalArgumentException    → 422 Unprocessable Entity</li>
 *   <li>IOException                 → 422 Unprocessable Entity</li>
 * </ul>
 */
@WebMvcTest(StatementController.class)
@DisplayName("StatementController — MockMvc Testleri")
class StatementControllerTest {

    private static final String UPLOAD_URL = "/api/v1/statements/upload";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatementService statementService;

    // =========================================================================
    // 200 OK — Başarılı yükleme
    // =========================================================================

    @Test
    @DisplayName("200 OK — Geçerli PDF, kaydedilen işlem sayısı ve dönem yanıtta döner")
    void upload_validFile_returns200WithCount() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenReturn("3 işlem başarıyla işlendi (2026-04-01 – 2026-04-30 dönemi).");

        mockMvc.perform(multipart(UPLOAD_URL).file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 işlem")));
    }

    // =========================================================================
    // 400 Bad Request
    // =========================================================================

    @Test
    @DisplayName("400 Bad Request — Dosya içeriği boş (0 byte)")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "bos.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart(UPLOAD_URL).file(emptyFile).param("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("boş olamaz")));
    }

    @Test
    @DisplayName("400 Bad Request — userId parametresi gönderilmedi")
    void upload_missingUserId_returns400() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).file(pdfFile("ekstre.pdf", "PDF")))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 404 Not Found
    // =========================================================================

    @Test
    @DisplayName("404 Not Found — Bilinmeyen userId (EntityNotFoundException → GlobalExceptionHandler)")
    void upload_unknownUserId_returns404() throws Exception {
        when(statementService.processUpload(any(), eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Kullanıcı bulunamadı: 99"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF"))
                        .param("userId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("bulunamadı")));
    }

    // =========================================================================
    // 409 Conflict — Mükerrer kayıt
    // =========================================================================

    @Test
    @DisplayName("409 Conflict — Aynı hash (HASH mükerreri)")
    void upload_sameHash_returns409() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new DuplicateStatementException(
                        "Bu dosya daha önce yüklendi. Aynı PDF içeriği sistemde zaten kayıtlı.",
                        DuplicateStatementException.Type.HASH, null, null
                ));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .param("userId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("daha önce yüklendi")))
                .andExpect(jsonPath("$.duplicateType").value("HASH"));
    }

    @Test
    @DisplayName("409 Conflict — Aynı dönem (PERIOD mükerreri), periodStart ve periodEnd yanıtta döner")
    void upload_samePeriod_returns409WithDates() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new DuplicateStatementException(
                        "Bu ekstre dönemi zaten kayıtlı! 2026-04-01 – 2026-04-30 aralığını kapsayan bir ekstre mevcut.",
                        DuplicateStatementException.Type.PERIOD, start, end
                ));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre2.pdf", "farklı içerik"))
                        .param("userId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.duplicateType").value("PERIOD"))
                .andExpect(jsonPath("$.periodStart").value("2026-04-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-04-30"))
                .andExpect(jsonPath("$.message").value(containsString("zaten kayıtlı")));
    }

    // =========================================================================
    // 422 Unprocessable Entity
    // =========================================================================

    @Test
    @DisplayName("422 Unprocessable Entity — PDF okunamadı (IOException → GlobalExceptionHandler)")
    void upload_ioException_returns422() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new IOException("PDF okunamadı"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("bozuk.pdf", "bozuk içerik"))
                        .param("userId", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("PDF okunamadı")));
    }

    @Test
    @DisplayName("422 Unprocessable Entity — LLM geçersiz JSON (IllegalArgumentException → GlobalExceptionHandler)")
    void upload_invalidJsonFromLlm_returns422() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new IllegalArgumentException("LLM geçerli bir JSON döndürmedi"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .param("userId", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("LLM geçerli")));
    }

    // =========================================================================
    // Yardımcı
    // =========================================================================

    private MockMultipartFile pdfFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes());
    }
}
