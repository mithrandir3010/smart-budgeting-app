package com.mali.smartbudget.controller;

import com.mali.smartbudget.exception.DuplicateStatementException;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.service.StatementService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatementController katman testi.
 *
 * <p>Filtreler ({@code addFilters = false}) devre dışı — JWT doğrulama burada test edilmez;
 * bu sorumluluk {@code SecurityFilterTest}'e aittir. Kimlik doğrulama,
 * {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#authentication}
 * ile SecurityContextHolder üzerinden enjekte edilir.
 *
 * <p>Kritik davranış: controller artık kullanıcı kimliğini request parametresinden değil,
 * {@code @AuthenticationPrincipal} aracılığıyla SecurityContext'ten alır.
 */
@WebMvcTest(StatementController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("StatementController — Katman Testleri")
class StatementControllerTest {

    private static final String UPLOAD_URL = "/api/v1/statements/upload";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatementService statementService;

    private User testUser;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("mali")
                .email("test@mali.com")
                .password("encoded_pass")
                .fullName("Mali Test Kullanıcısı")
                .role("ROLE_USER")
                .build();
        auth = new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
    }

    // =========================================================================
    // 200 OK
    // =========================================================================

    @Test
    @DisplayName("200 OK — Geçerli auth + PDF → başarı mesajı döner")
    void upload_authenticatedUser_validFile_returns200() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenReturn("3 işlem başarıyla işlendi (2026-04-01 – 2026-04-30 dönemi).");

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 işlem")));
    }

    @Test
    @DisplayName("userId, SecurityContext'ten (principal) alınır — request parametresinden ALINMAZ")
    void upload_userId_comesFromSecurityContext_notFromRequestParam() throws Exception {
        when(statementService.processUpload(any(), anyLong(), any())).thenReturn("OK");

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF"))
                        .with(authentication(auth)));

        // id=1 sadece SecurityContext'teki User'dan gelir, hardcoded değil
        verify(statementService).processUpload(any(), eq(1L), any());
    }

    @Test
    @DisplayName("Orijinal dosya adı processUpload'a aktarılır")
    void upload_originalFilename_passedToService() throws Exception {
        when(statementService.processUpload(any(), anyLong(), eq("mayis-2026.pdf")))
                .thenReturn("Başarılı");

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("mayis-2026.pdf", "içerik"))
                        .with(authentication(auth)))
                .andExpect(status().isOk());

        verify(statementService).processUpload(any(), eq(1L), eq("mayis-2026.pdf"));
    }

    // =========================================================================
    // 400 Bad Request
    // =========================================================================

    @Test
    @DisplayName("400 Bad Request — Boş dosya (0 byte)")
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "bos.pdf", "application/pdf", new byte[0]
        );

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(emptyFile)
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("boş olamaz")));
    }

    // =========================================================================
    // 404 Not Found
    // =========================================================================

    @Test
    @DisplayName("404 Not Found — EntityNotFoundException → GlobalExceptionHandler")
    void upload_userNotFound_returns404() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new EntityNotFoundException("Kullanıcı bulunamadı: 1"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF"))
                        .with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("bulunamadı")));
    }

    // =========================================================================
    // 409 Conflict — Mükerrer kayıt
    // =========================================================================

    @Test
    @DisplayName("409 Conflict — Aynı PDF hash'i (HASH mükerreri)")
    void upload_sameHash_returns409() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new DuplicateStatementException(
                        "Bu dosya daha önce yüklendi. Aynı PDF içeriği sistemde zaten kayıtlı.",
                        DuplicateStatementException.Type.HASH, null, null
                ));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .with(authentication(auth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("daha önce yüklendi")))
                .andExpect(jsonPath("$.duplicateType").value("HASH"));
    }

    @Test
    @DisplayName("409 Conflict — Dönem çakışması (PERIOD mükerreri), tarihler yanıtta döner")
    void upload_samePeriod_returns409WithDates() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end   = LocalDate.of(2026, 4, 30);

        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new DuplicateStatementException(
                        "Bu ekstre dönemi zaten kayıtlı!",
                        DuplicateStatementException.Type.PERIOD, start, end
                ));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre2.pdf", "farklı içerik"))
                        .with(authentication(auth)))
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
    @DisplayName("422 Unprocessable Entity — PDF okunamadı (IOException)")
    void upload_ioException_returns422() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new IOException("PDF okunamadı"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("bozuk.pdf", "bozuk içerik"))
                        .with(authentication(auth)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("PDF okunamadı")));
    }

    @Test
    @DisplayName("422 Unprocessable Entity — LLM geçersiz JSON döndürdü (IllegalArgumentException)")
    void upload_invalidJsonFromLlm_returns422() throws Exception {
        when(statementService.processUpload(any(), eq(1L), any()))
                .thenThrow(new IllegalArgumentException("LLM geçerli bir JSON döndürmedi"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pdfFile("ekstre.pdf", "PDF içeriği"))
                        .with(authentication(auth)))
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
