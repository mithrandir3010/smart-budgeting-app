package com.mali.smartbudget.controller;

import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.ExtractionService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatementController.class)
@DisplayName("StatementController — MockMvc Testleri")
class StatementControllerTest {

    private static final String UPLOAD_URL = "/api/v1/statements/upload";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExtractionService extractionService;

    // =========================================================================
    // Başarılı yükleme
    // =========================================================================

    @Test
    @DisplayName("200 OK — Geçerli PDF, kaydedilen işlem sayısı yanıtta döner")
    void upload_validFile_returns200WithCount() throws Exception {
        User user = User.builder().id(1L).email("x@x.com").fullName("X").password("p").build();
        List<Transaction> saved = List.of(
                Transaction.builder().user(user).date(LocalDate.of(2026, 4, 1))
                        .description("Migros").amount(new BigDecimal("245.90"))
                        .category("Market").currency("TRY").build(),
                Transaction.builder().user(user).date(LocalDate.of(2026, 4, 3))
                        .description("Kira").amount(new BigDecimal("12000.00"))
                        .category("Kira").currency("TRY").build()
        );
        when(extractionService.extractAndMap(any(), eq(1L))).thenReturn(saved);

        MockMultipartFile file = pdfFile("ekstre.pdf", "PDF içeriği");

        mockMvc.perform(multipart(UPLOAD_URL).file(file).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 işlem")));
    }

    // =========================================================================
    // İstemci hataları (4xx)
    // =========================================================================

    @Test
    @DisplayName("400 Bad Request — Dosya içeriği boş")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "bos.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart(UPLOAD_URL).file(emptyFile).param("userId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("boş olamaz")));
    }

    @Test
    @DisplayName("400 Bad Request — Bilinmeyen userId (EntityNotFoundException)")
    void upload_unknownUserId_returns400() throws Exception {
        when(extractionService.extractAndMap(any(), eq(99L)))
                .thenThrow(new EntityNotFoundException("Kullanıcı bulunamadı: 99"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF"))
                        .param("userId", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("bulunamadı")));
    }

    @Test
    @DisplayName("400 Bad Request — userId parametresi gönderilmedi")
    void upload_missingUserId_returns400() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).file(pdfFile("ekstre.pdf", "PDF")))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Sunucu hataları (422)
    // =========================================================================

    @Test
    @DisplayName("422 Unprocessable Entity — PDF okunamadı (IOException)")
    void upload_ioException_returns422() throws Exception {
        when(extractionService.extractAndMap(any(), eq(1L)))
                .thenThrow(new IOException("PDF okunamadı"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("bozuk.pdf", "bozuk içerik"))
                        .param("userId", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("PDF okunamadı")));
    }

    @Test
    @DisplayName("422 Unprocessable Entity — LLM geçersiz JSON döndürdü (IllegalArgumentException)")
    void upload_invalidJsonFromLlm_returns422() throws Exception {
        when(extractionService.extractAndMap(any(), eq(1L)))
                .thenThrow(new IllegalArgumentException("LLM geçerli bir JSON döndürmedi"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .param("userId", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(containsString("ayıklanamadı")));
    }

    // =========================================================================
    // Yardımcı
    // =========================================================================

    private MockMultipartFile pdfFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "application/pdf", content.getBytes());
    }
}
