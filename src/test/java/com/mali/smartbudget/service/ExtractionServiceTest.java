package com.mali.smartbudget.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mali.smartbudget.dto.TransactionDto;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ExtractionService — Birim Testleri
 *
 * ExtractionService artık saf I/O sorumluluğuna sahip:
 *   extractDtos(file) → List<TransactionDto>  (DB işlemi yok)
 * DB işlemleri (delete + save) StatementService'e taşındı.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionService — Birim Testleri")
class ExtractionServiceTest {

    @Mock private ChatLanguageModel chatLanguageModel;
    @Mock private PdfService pdfService;

    @InjectMocks
    private ExtractionService extractionService;

    private MockMultipartFile dummyFile;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ReflectionTestUtils.setField(extractionService, "objectMapper", mapper);

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
    // extractDtos — Happy Path
    // =========================================================================

    @Test
    @DisplayName("Mock modda extractDtos doğru DTO listesi döner")
    void extractDtos_mockMode_returnsExpectedDtos() throws IOException {
        setApiKey("your-api-key-here");

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).isNotEmpty();
        assertThat(dtos.get(0).description()).isEqualTo("Migros Market");
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("245.90");
        assertThat(dtos.get(0).category()).isEqualTo("Market");
        assertThat(dtos.get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("Mock modda abonelik işaretleri doğru gelir")
    void extractDtos_mockMode_subscriptionFlagsCorrect() throws IOException {
        setApiKey("your-api-key-here");

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        // Mock JSON'da Netflix, Spotify, iCloud subscription=true
        long subscriptions = dtos.stream().filter(TransactionDto::isSubscription).count();
        assertThat(subscriptions).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("LLM geçersiz JSON döndürdüğünde IllegalArgumentException fırlatılır")
    void extractDtos_invalidJsonFromLlm_throwsIllegalArgumentException() throws IOException {
        setLlmMode();
        when(pdfService.extractText(any())).thenReturn("PDF metin içeriği");
        doReturn("Bu JSON değil, düz metin.").when(chatLanguageModel).generate(anyString());

        assertThatThrownBy(() -> extractionService.extractDtos(dummyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM geçerli bir JSON döndürmedi");
    }

    @Test
    @DisplayName("LLM JSON bloğu içinde döndürdüğünde (```json ... ```) temizlenir ve parse edilir")
    void extractDtos_llmReturnsJsonBlock_strippedAndParsed() throws IOException {
        setLlmMode();
        when(pdfService.extractText(any())).thenReturn("15.04.2026 Migros Market 250,00 TL");
        doReturn("""
                ```json
                [
                  {"date": "2026-04-01", "description": "Test", "amount": 100.00, "category": "Diğer", "currency": "TRY"}
                ]
                ```""").when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Test");
    }

    @Test
    @DisplayName("PdfService IOException fırlattığında dış katmana yayılır")
    void extractDtos_pdfServiceThrowsIOException_propagated() throws IOException {
        setLlmMode();
        when(pdfService.extractText(any())).thenThrow(new IOException("PDF okunamadı"));

        assertThatThrownBy(() -> extractionService.extractDtos(dummyFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PDF okunamadı");
    }

    // =========================================================================
    // Tarih Formatları — LLM farklı formatlar döndürebilir
    // =========================================================================

    @Test
    @DisplayName("Tarih dd.MM.yyyy formatında parse edilir (05.04.2026)")
    void extractDtos_dateFormat_ddmmyyyyDot_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05.04.2026","description":"Migros","amount":100.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih dd/MM/yyyy formatında parse edilir (05/04/2026)")
    void extractDtos_dateFormat_ddmmyyyySlash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05/04/2026","description":"Starbucks","amount":89.50,"category":"Kafe","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih yyyy/MM/dd formatında parse edilir (2026/04/05)")
    void extractDtos_dateFormat_yyyymmddSlash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026/04/05","description":"Shell","amount":450.00,"category":"Akaryakıt","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih d.M.yyyy kısa formatında parse edilir (5.4.2026)")
    void extractDtos_dateFormat_shortDot_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"5.4.2026","description":"Carrefour","amount":320.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Tarih dd-MM-yyyy formatında parse edilir (05-04-2026)")
    void extractDtos_dateFormat_ddmmyyyyDash_parsedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"05-04-2026","description":"BİM","amount":178.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    @DisplayName("Desteklenen tüm tarih formatları tek JSON'da parse edilir")
    void extractDtos_allDateFormats_allRowsParsed() throws IOException {
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

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(6);
        assertThat(dtos.stream().map(TransactionDto::date))
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
    void extractDtos_turkishAmountFormat_normalizedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Migros","amount":"1.234,56","category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("1234.56");
    }

    @Test
    @DisplayName("Virgüllü ondalık string tutar ('89,50') → 89.50 olarak normalize edilir")
    void extractDtos_commaDecimalAmount_normalizedCorrectly() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Kafe","amount":"89,50","category":"Kafe","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("89.50");
    }

    @Test
    @DisplayName("Integer tutar (245) → 245.00 olarak normalize edilir")
    void extractDtos_integerAmount_normalizedWithTwoDecimals() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"BİM","amount":245,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("245.00");
    }

    // =========================================================================
    // Row Atlama Senaryoları — Hatalı satırlar atlanır, geçerliler korunur
    // =========================================================================

    @Test
    @DisplayName("null tarihli satır atlanır; geçerli satırlar döner")
    void extractDtos_nullDate_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":null,"description":"NullDate","amount":300.00,"category":"Market","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":100.00,"category":"Kafe","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Geçersiz tarih formatı ('April 5, 2026') → satır atlanır")
    void extractDtos_invalidDateFormat_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"April 5, 2026","description":"BadDate","amount":200.00,"category":"Kira","currency":"TRY"},
                  {"date":"2026-04-01","description":"Geçerli","amount":500.00,"category":"Market","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("null tutarlı satır atlanır")
    void extractDtos_nullAmount_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"NullAmount","amount":null,"category":"Market","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":150.00,"category":"Kafe","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Sıfır tutarlı satır atlanır (0.00 → geçersiz işlem)")
    void extractDtos_zeroAmount_rowSkipped() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":"2026-04-01","description":"ZeroAmount","amount":0.00,"category":"Diğer","currency":"TRY"},
                  {"date":"2026-04-02","description":"Geçerli","amount":250.00,"category":"Market","currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Geçerli");
    }

    @Test
    @DisplayName("Karma JSON: 5 satır, 3'ü geçersiz → 2 geçerli satır döner")
    void extractDtos_partialFailure_onlyValidRowsReturned() throws IOException {
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

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.stream().map(TransactionDto::description))
                .containsExactlyInAnyOrder("Geçerli1", "Geçerli2");
    }

    @Test
    @DisplayName("Tüm satırlar geçersizse IllegalArgumentException fırlatılır")
    void extractDtos_allRowsInvalid_throwsIllegalArgumentException() throws IOException {
        setLlmMode();
        String json = """
                [
                  {"date":null,"description":"NullDate1","amount":100.00,"category":"Market","currency":"TRY"},
                  {"date":null,"description":"NullDate2","amount":200.00,"category":"Kafe", "currency":"TRY"}
                ]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        assertThatThrownBy(() -> extractionService.extractDtos(dummyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dosya formatı analiz edilemedi");
    }

    // =========================================================================
    // Varsayılan Alan Değerleri — null alanlar için fallback
    // =========================================================================

    @Test
    @DisplayName("null description → 'Bilinmeyen' varsayılanı kullanılır")
    void extractDtos_nullDescription_defaultsToBilinmeyen() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":null,"amount":100.00,"category":"Market","currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Bilinmeyen");
    }

    @Test
    @DisplayName("null category → 'Diğer' varsayılanı kullanılır")
    void extractDtos_nullCategory_defaultsToDiger() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":null,"currency":"TRY"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).category()).isEqualTo("Diğer");
    }

    @Test
    @DisplayName("null currency → 'TRY' varsayılanı kullanılır")
    void extractDtos_nullCurrency_defaultsToTRY() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":"Market","currency":null}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("boş string currency → 'TRY' varsayılanı kullanılır")
    void extractDtos_emptyCurrency_defaultsToTRY() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Test","amount":100.00,"category":"Market","currency":""}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("isSubscription string 'true' → boolean true olarak parse edilir")
    void extractDtos_isSubscriptionStringTrue_parsedAsTrue() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Netflix","amount":79.99,"category":"Eğlence","currency":"TRY","isSubscription":"true"}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).isSubscription()).isTrue();
    }

    @Test
    @DisplayName("isSubscription null → false varsayılan değeri kullanılır")
    void extractDtos_isSubscriptionNull_defaultsFalse() throws IOException {
        setLlmMode();
        String json = """
                [{"date":"2026-04-01","description":"Market","amount":200.00,"category":"Market","currency":"TRY","isSubscription":null}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).isSubscription()).isFalse();
    }

    // =========================================================================
    // Yardımcılar
    // =========================================================================

    private void setApiKey(String key) {
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", key);
    }

    private void setLlmMode() {
        ReflectionTestUtils.setField(extractionService, "forceMockMode", false);
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", "sk-test-real-looking-key");
    }
}
