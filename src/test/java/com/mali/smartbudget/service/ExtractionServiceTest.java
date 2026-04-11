package com.mali.smartbudget.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionService — Birim Testleri")
class ExtractionServiceTest {

    @Mock private ChatLanguageModel chatLanguageModel;
    @Mock private PdfService pdfService;
    @Mock private UserRepository userRepository;
    @Mock private TransactionService transactionService;

    @InjectMocks
    private ExtractionService extractionService;

    private User testUser;
    private MockMultipartFile dummyFile;

    @BeforeEach
    void setUp() throws Exception {
        // ObjectMapper'ı servis içine manuel inject et (Mockito @Value'yu enjekte etmez)
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(extractionService, "objectMapper", mapper);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Test Kullanıcı")
                .password("secret")
                .build();

        dummyFile = new MockMultipartFile(
                "file", "ekstre.pdf", "application/pdf", "PDF içeriği".getBytes()
        );
    }

    // =========================================================================
    // Mock Mode — API key geçersiz olduğunda
    // =========================================================================

    @Test
    @DisplayName("Mock mode'da LLM çağrılmaz, sabit JSON döner")
    void extractTransactionsAsJson_mockMode_returnsHardcodedJson() throws IOException {
        setApiKey("your-api-key-here"); // placeholder → mock mode

        String json = extractionService.extractTransactionsAsJson(dummyFile);

        assertThat(json).contains("Migros Market");
        verifyNoInteractions(chatLanguageModel);
        verifyNoInteractions(pdfService);
    }

    @Test
    @DisplayName("Boş API key mock mode'u tetikler")
    void extractTransactionsAsJson_emptyApiKey_triggersMockMode() throws IOException {
        setApiKey("");

        String json = extractionService.extractTransactionsAsJson(dummyFile);

        assertThat(json).isNotBlank();
        verifyNoInteractions(chatLanguageModel);
    }

    @Test
    @DisplayName("'sk-' ile başlamayan key mock mode'u tetikler")
    void extractTransactionsAsJson_invalidKeyPrefix_triggersMockMode() throws IOException {
        setApiKey("invalid-key-12345");

        String json = extractionService.extractTransactionsAsJson(dummyFile);

        assertThat(json).isNotBlank();
        verifyNoInteractions(chatLanguageModel);
    }

    // =========================================================================
    // extractAndMap — Happy Path
    // =========================================================================

    @Test
    @DisplayName("Başarılı akış: eski veriler silinir, yeni transaction'lar kaydedilir")
    void extractAndMap_happyPath_deletesOldAndSavesNew() throws IOException {
        setApiKey("your-api-key-here"); // mock mode → LLM çağrısı yapılmaz

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        List<Transaction> savedTransactions = List.of(
                buildTransaction("Migros Market", "245.90", "Market"),
                buildTransaction("Starbucks Kahve", "89.50", "Kafe"),
                buildTransaction("Kira Ödemesi", "12000.00", "Kira")
        );
        when(transactionService.saveAllTransactions(any())).thenReturn(savedTransactions);

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        // deleteAllByUserId KAYDETMEDEN ÖNCE çağrılmalı
        InOrder inOrder = inOrder(transactionService);
        inOrder.verify(transactionService).deleteAllByUserId(1L);
        inOrder.verify(transactionService).saveAllTransactions(any());

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Kaydedilen transaction'lar doğru alanlarla oluşturulur")
    void extractAndMap_happyPath_transactionFieldsMappedCorrectly() throws IOException {
        setApiKey("your-api-key-here");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        // Mock JSON'daki ilk kayıt: Migros Market, 245.90, Market, TRY, 2026-04-01
        Transaction first = result.get(0);
        assertThat(first.getDescription()).isEqualTo("Migros Market");
        assertThat(first.getAmount()).isEqualByComparingTo("245.90");
        assertThat(first.getCategory()).isEqualTo("Market");
        assertThat(first.getCurrency()).isEqualTo("TRY");
        assertThat(first.getUser()).isEqualTo(testUser);
    }

    // =========================================================================
    // deleteAllByUserId davranışı
    // =========================================================================

    @Test
    @DisplayName("deleteAllByUserId her zaman saveAll'dan önce çağrılır")
    void extractAndMap_deleteAlwaysCalledBeforeSave() throws IOException {
        setApiKey("your-api-key-here");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenReturn(List.of());

        extractionService.extractAndMap(dummyFile, 1L);

        InOrder inOrder = inOrder(transactionService);
        inOrder.verify(transactionService).deleteAllByUserId(1L);
        inOrder.verify(transactionService).saveAllTransactions(any());
    }

    @Test
    @DisplayName("Kullanıcı bulunamazsa deleteAllByUserId çağrılmaz")
    void extractAndMap_userNotFound_deleteNeverCalled() {
        setApiKey("your-api-key-here");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extractionService.extractAndMap(dummyFile, 99L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(transactionService, never()).deleteAllByUserId(anyLong());
        verify(transactionService, never()).saveAllTransactions(any());
    }

    // =========================================================================
    // Hata senaryoları
    // =========================================================================

    @Test
    @DisplayName("Bilinmeyen userId ile istek — EntityNotFoundException fırlatılır")
    void extractAndMap_unknownUserId_throwsEntityNotFoundException() {
        setApiKey("your-api-key-here");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> extractionService.extractAndMap(dummyFile, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("LLM geçersiz JSON döndürdüğünde IllegalArgumentException fırlatılır")
    void extractAndMap_invalidJsonFromLlm_throwsIllegalArgumentException() throws IOException {
        setApiKey("sk-real-key-from-openai"); // real mode → LLM çağrısı
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(pdfService.extractText(any())).thenReturn("PDF metin içeriği");
        doReturn("Bu JSON değil, düz metin.").when(chatLanguageModel).generate(anyString());

        assertThatThrownBy(() -> extractionService.extractAndMap(dummyFile, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM geçerli bir JSON döndürmedi");
    }

    @Test
    @DisplayName("LLM JSON bloğu içinde döndürdüğünde (```json ... ```) temizlenir ve parse edilir")
    void extractAndMap_llmReturnsJsonBlock_strippedAndParsed() throws IOException {
        setApiKey("sk-real-key-from-openai");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(pdfService.extractText(any())).thenReturn("PDF");
        doReturn("""
                ```json
                [
                  {"date": "2026-04-01", "description": "Test", "amount": 100.00, "category": "Diğer", "currency": "TRY"}
                ]
                ```""").when(chatLanguageModel).generate(anyString());
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Test");
    }

    @Test
    @DisplayName("PdfService IOException fırlattığında dış katmana yayılır")
    void extractAndMap_pdfServiceThrowsIOException_propagated() throws IOException {
        setApiKey("sk-real-key-from-openai");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(pdfService.extractText(any())).thenThrow(new IOException("PDF okunamadı"));

        assertThatThrownBy(() -> extractionService.extractAndMap(dummyFile, 1L))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PDF okunamadı");
    }

    // =========================================================================
    // Yardımcılar
    // =========================================================================

    private void setApiKey(String key) {
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", key);
    }

    private Transaction buildTransaction(String description, String amount, String category) {
        return Transaction.builder()
                .user(testUser)
                .date(LocalDate.of(2026, 4, 1))
                .description(description)
                .amount(new BigDecimal(amount))
                .category(category)
                .currency("TRY")
                .build();
    }
}
