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
    // Tarih Formatları — LLM farklı formatlar döndürebilir
    // =========================================================================

    @Test
    @DisplayName("Tarih dd.MM.yyyy formatında parse edilir (05.04.2026)")
    void extractAndMap_dateFormat_ddmmyyyyDot_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05.04.2026","description":"Migros","amount":100.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih dd/MM/yyyy formatında parse edilir (05/04/2026)")
    void extractAndMap_dateFormat_ddmmyyyySlash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05/04/2026","description":"Starbucks","amount":89.50,"category":"Kafe","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih yyyy/MM/dd formatında parse edilir (2026/04/05)")
    void extractAndMap_dateFormat_yyyymmddSlash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026/04/05","description":"Shell","amount":450.00,"category":"Akaryakıt","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih d.M.yyyy kısa formatında parse edilir (5.4.2026)")
    void extractAndMap_dateFormat_shortDot_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"5.4.2026","description":"Carrefour","amount":320.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih dd-MM-yyyy formatında parse edilir (05-04-2026)")
    void extractAndMap_dateFormat_ddmmyyyyDash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05-04-2026","description":"BİM","amount":178.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Desteklenen tüm tarih formatları tek JSON'da parse edilir")
    void extractAndMap_allDateFormats_allRowsParsed() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"ISO",   "amount":10,"category":"Diğer","currency":"TRY"},
                  {"date":"02.04.2026","description":"Dot",   "amount":20,"category":"Diğer","currency":"TRY"},
                  {"date":"03/04/2026","description":"Slash", "amount":30,"category":"Diğer","currency":"TRY"},
                  {"date":"2026/04/04","description":"YSlash","amount":40,"category":"Diğer","currency":"TRY"},
                  {"date":"5.4.2026", "description":"Short",  "amount":50,"category":"Diğer","currency":"TRY"},
                  {"date":"06-04-2026","description":"Dash",  "amount":60,"category":"Diğer","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(6);
        assertThat(result.stream().map(Transaction::getDate))
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2),
                        LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 4),
                        LocalDate.of(2026, 4, 5), LocalDate.of(2026, 4, 6));
    }

    // =========================================================================
    // Tutar Formatları — AmountNormalizer entegrasyonu
    // =========================================================================

    @Test
    @DisplayName("Türk formatı string tutar ('1.234,56') → 1234.56 olarak normalize edilir")
    void extractAndMap_turkishAmountFormat_normalizedCorrectly() throws IOException {
        setLlmMode();
        // LLM bazen Türk formatında string döner
        String json = """
                [{"date":"2026-04-01","description":"Migros","amount":"1.234,56","category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("1234.56");
    }

    @Test
    @DisplayName("Virgüllü ondalık string tutar ('89,50') → 89.50 olarak normalize edilir")
    void extractAndMap_commaDecimalAmount_normalizedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Kafe","amount":"89,50","category":"Kafe","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("89.50");
    }

    @Test
    @DisplayName("Integer tutar (245) → 245.00 olarak normalize edilir")
    void extractAndMap_integerAmount_normalizedWithTwoDecimals() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"BİM","amount":245,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("245.00");
    }

    // =========================================================================
    // Row Atlama Senaryoları — Hatalı satırlar atlanır, geçerliler korunur
    // =========================================================================

    @Test
    @DisplayName("null tarihli satır atlanır; geçerli satırlar kaydedilir")
    void extractAndMap_nullDate_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":null,"description":"NullDate","amount":300.00,"category":"Market","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":100.00,"category":"Kafe","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Geçersiz tarih formatı ('April 5, 2026') → satır atlanır")
    void extractAndMap_invalidDateFormat_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"April 5, 2026","description":"BadDate","amount":200.00,"category":"Kira","currency":"TRY"},
                  {"date":"2026-04-01","description":"Geçerli","amount":500.00,"category":"Market","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("null tutarlı satır atlanır")
    void extractAndMap_nullAmount_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"NullAmount","amount":null,"category":"Market","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":150.00,"category":"Kafe","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Sıfır tutarlı satır atlanır (0.00 → geçersiz işlem)")
    void extractAndMap_zeroAmount_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"ZeroAmount","amount":0.00,"category":"Diğer","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":250.00,"category":"Market","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Karma JSON: 5 satır, 3'ü geçersiz → 2 geçerli satır kaydedilir")
    void extractAndMap_partialFailure_onlyValidRowsSaved() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"Geçerli1","amount":100.00,"category":"Market","currency":"TRY"},
                  {"date":null,        "description":"NullDate", "amount":200.00,"category":"Market","currency":"TRY"},
                  {"date":"2026-04-03","description":"Geçerli2","amount":300.00,"category":"Kafe", "currency":"TRY"},
                  {"date":"INVALID",   "description":"BadDate",  "amount":400.00,"category":"Kira", "currency":"TRY"},
                  {"date":"2026-04-05","description":"ZeroAmt",  "amount":0.00,  "category":"Diğer","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Transaction::getDescription))
                .containsExactlyInAnyOrder("Geçerli1", "Geçerli2");
    }

    @Test
    @DisplayName("Tüm satırlar geçersizse IllegalArgumentException fırlatılır")
    void extractAndMap_allRowsInvalid_throwsIllegalArgumentException() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":null,"description":"NullDate1","amount":100.00,"category":"Market","currency":"TRY"},
                  {"date":null,"description":"NullDate2","amount":200.00,"category":"Kafe", "currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> extractionService.extractAndMap(dummyFile, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hiçbir geçerli işlem");
    }

    // =========================================================================
    // Varsayılan Alan Değerleri — null alanlar için fallback
    // =========================================================================

    @Test
    @DisplayName("null description → 'Bilinmeyen' varsayılanı kullanılır")
    void extractAndMap_nullDescription_defaultsToBilinmeyen() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":null,"amount":100.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDescription()).isEqualTo("Bilinmeyen");
    }

    @Test
    @DisplayName("null category → 'Diğer' varsayılanı kullanılır")
    void extractAndMap_nullCategory_defaultsToDiger() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":null,"currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Diğer");
    }

    @Test
    @DisplayName("null currency → 'TRY' varsayılanı kullanılır")
    void extractAndMap_nullCurrency_defaultsToTRY() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":"Market","currency":null}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("boş string currency → 'TRY' varsayılanı kullanılır")
    void extractAndMap_emptyCurrency_defaultsToTRY() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":"Market","currency":""}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("isSubscription string 'true' → boolean true olarak parse edilir")
    void extractAndMap_isSubscriptionStringTrue_parsedAsTrue() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Netflix","amount":79.99,"category":"Eğlence","currency":"TRY","isSubscription":"true"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isSubscription()).isTrue();
    }

    @Test
    @DisplayName("isSubscription null → false varsayılan değeri kullanılır")
    void extractAndMap_isSubscriptionNull_defaultsFalse() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Market","amount":200.00,"category":"Market","currency":"TRY","isSubscription":null}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(transactionService.saveAllTransactions(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Transaction> result = extractionService.extractAndMap(dummyFile, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isSubscription()).isFalse();
    }

    // =========================================================================
    // Yardımcılar
    // =========================================================================

    private void setApiKey(String key) {
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", key);
    }

    /**
     * Mock mode'dan çıkar ve LLM moduna geçer.
     * {@code forceMockMode=false} + "sk-..." API key → {@code isMockMode()} false döner.
     */
    private void setLlmMode() {
        ReflectionTestUtils.setField(extractionService, "forceMockMode", false);
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", "sk-test-real-looking-key");
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
