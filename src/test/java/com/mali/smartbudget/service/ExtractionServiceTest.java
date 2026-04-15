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
        ReflectionTestUtils.setField(extractionService, "openAiApiKey", "sk-test-real-looking-key");

        dummyFile = new MockMultipartFile(
                "file", "ekstre.pdf", "application/pdf", "PDF içeriği".getBytes()
        );
    }

    // =========================================================================
    // extractDtos — Happy Path
    // =========================================================================

    @Test
    @DisplayName("LLM düz metin döndürdüğünde repairJson [] üretir ve IllegalArgumentException fırlatılır")
    void extractDtos_invalidJsonFromLlm_throwsIllegalArgumentException() throws IOException {
        // repairJson hiç '}' bulamayınca [] döndürür → dtos boş → "işlem bulunamadı" hatası
        when(pdfService.extractText(any())).thenReturn("PDF metin içeriği");
        doReturn("Bu JSON değil, düz metin.").when(chatLanguageModel).generate(anyString());

        assertThatThrownBy(() -> extractionService.extractDtos(dummyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("harcama işlemi bulunamadı");
    }

    @Test
    @DisplayName("LLM JSON bloğu içinde döndürdüğünde (```json ... ```) temizlenir ve parse edilir")
    void extractDtos_llmReturnsJsonBlock_strippedAndParsed() throws IOException {
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
    // Türk Sayı Formatı — sanitizeJson + extractDtos entegrasyon testleri
    // =========================================================================

    @Test
    @DisplayName("LLM 1.250.00 yazarsa (iki nokta) → Jackson hata vermeden 1250.00 parse edilir")
    void extractDtos_doubleDotAmount_sanitizedAndParsed() throws IOException {
        // Bu tam olarak bildirilen hatayı simüle eder: "amount": 1.250.00
        String malformedJson = """
                [{"date":"2026-04-01","description":"Migros","amount":1.250.00,"category":"Market","currency":"TRY","isSubscription":false}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(malformedJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("1250.00");
    }

    @Test
    @DisplayName("LLM 12.480.37 yazarsa (büyük tutar, iki nokta) → 12480.37 parse edilir")
    void extractDtos_largeTurkishDoubleDot_sanitizedAndParsed() throws IOException {
        String malformedJson = """
                [{"date":"2026-04-05","description":"Kira","amount":12.480.37,"category":"Kira","currency":"TRY","isSubscription":false}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(malformedJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("12480.37");
    }

    @Test
    @DisplayName("LLM 1.250,00 yazarsa (Türk formatı bare number) → 1250.00 parse edilir")
    void extractDtos_turkishCommaInBareNumber_sanitizedAndParsed() throws IOException {
        // Virgül JSON'da number içinde geçemez — sanitizer düzeltmeli
        String malformedJson = "[{\"date\":\"2026-04-01\",\"description\":\"Shell\",\"amount\":1.250,00,\"category\":\"Akaryakıt\",\"currency\":\"TRY\",\"isSubscription\":false}]";
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(malformedJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).amount()).isEqualByComparingTo("1250.00");
    }

    // =========================================================================
    // fixAmountString — birim testleri
    // =========================================================================

    @Test
    @DisplayName("fixAmountString: geçerli değerlere dokunmaz")
    void fixAmountString_alreadyValid_unchanged() {
        assertThat(extractionService.fixAmountString("1250.00")).isEqualTo("1250.00");
        assertThat(extractionService.fixAmountString("89.90")).isEqualTo("89.90");
        assertThat(extractionService.fixAmountString("12000")).isEqualTo("12000");
        assertThat(extractionService.fixAmountString("5.5")).isEqualTo("5.5");
    }

    @Test
    @DisplayName("fixAmountString: iki nokta → son nokta ondalık, öncekiler binlik")
    void fixAmountString_multiDot_fixed() {
        assertThat(extractionService.fixAmountString("1.250.00")).isEqualTo("1250.00");
        assertThat(extractionService.fixAmountString("12.480.37")).isEqualTo("12480.37");
        assertThat(extractionService.fixAmountString("1.234.567.89")).isEqualTo("1234567.89");
    }

    @Test
    @DisplayName("fixAmountString: Türk formatı (virgül=ondalık, nokta=binlik) → standart")
    void fixAmountString_turkishFormat_fixed() {
        assertThat(extractionService.fixAmountString("1.250,00")).isEqualTo("1250.00");
        assertThat(extractionService.fixAmountString("12.480,37")).isEqualTo("12480.37");
        assertThat(extractionService.fixAmountString("89,90")).isEqualTo("89.90");
        assertThat(extractionService.fixAmountString("49,99")).isEqualTo("49.99");
    }

    @Test
    @DisplayName("fixAmountString: tek nokta + 3 haneli son kısım → binlik ayraç kaldırılır")
    void fixAmountString_singleDotThreeDigits_treatedAsThousands() {
        assertThat(extractionService.fixAmountString("1.250")).isEqualTo("1250");
        assertThat(extractionService.fixAmountString("12.000")).isEqualTo("12000");
    }

    @Test
    @DisplayName("sanitizeJson: karışık tutarlar içeren JSON'ı düzeltir")
    void sanitizeJson_mixedAmounts_allFixed() {
        String input = """
                [
                  {"amount":1.250.00},
                  {"amount":12.480,37},
                  {"amount":"89,90"},
                  {"amount":1500.00},
                  {"amount":49.99}
                ]
                """;
        String result = extractionService.sanitizeJson(input);

        assertThat(result).contains("\"amount\":1250.00");
        assertThat(result).contains("\"amount\":12480.37");
        assertThat(result).contains("\"amount\":89.90");
        assertThat(result).contains("\"amount\":1500.00");
        assertThat(result).contains("\"amount\":49.99");
        // Tırnak karakterleri kaldırılmış olmalı
        assertThat(result).doesNotContain("\"amount\":\"");
    }

    // =========================================================================
    // repairJson — truncated JSON onarımı
    // =========================================================================

    @Test
    @DisplayName("repairJson: zaten geçerli JSON'a dokunmaz")
    void repairJson_alreadyValid_unchanged() {
        String valid = "[{\"date\":\"2026-04-01\",\"description\":\"Migros\",\"amount\":250.00,\"category\":\"Market\",\"currency\":\"TRY\",\"isSubscription\":false}]";
        assertThat(extractionService.repairJson(valid)).isEqualTo(valid);
    }

    @Test
    @DisplayName("repairJson: ] eksik → son tam } sonrasına ] ekler")
    void repairJson_missingClosingBracket_repaired() {
        // LLM cevabı yarıda kesildi: son obje eksik
        String truncated = "[{\"date\":\"2026-04-01\",\"amount\":250.00},{\"date\":\"2026-04-02\",\"amount\":100.00";
        String repaired  = extractionService.repairJson(truncated);

        assertThat(repaired).endsWith("]");
        assertThat(repaired).startsWith("[");
        // İlk tam obje kurtarılmış olmalı
        assertThat(repaired).contains("250.00");
        // Yarım obje atılmış olmalı
        assertThat(repaired).doesNotContain("100.00");
    }

    @Test
    @DisplayName("repairJson: sondaki virgül temizlenir ve ] eklenir")
    void repairJson_trailingComma_cleaned() {
        String truncated = "[{\"date\":\"2026-04-01\",\"amount\":250.00},";
        String repaired  = extractionService.repairJson(truncated);

        assertThat(repaired).isEqualTo("[{\"date\":\"2026-04-01\",\"amount\":250.00}]");
    }

    @Test
    @DisplayName("repairJson: hiç tam obje yoksa [] döner")
    void repairJson_noCompleteObject_returnsEmptyArray() {
        assertThat(extractionService.repairJson("[{\"date\":\"2026")).isEqualTo("[]");
        assertThat(extractionService.repairJson("")).isEqualTo("[]");
    }

    @Test
    @DisplayName("extractDtos: truncated JSON → onarıldıktan sonra tam objeler parse edilir")
    void extractDtos_truncatedJson_repairedAndParsed() throws IOException {
        // İki tam obje + başlamış ama bitmemiş üçüncü obje
        String truncated = """
                [
                  {"date":"2026-04-01","description":"Migros","amount":250.00,"category":"Market","currency":"TRY","isSubscription":false},
                  {"date":"2026-04-02","description":"Starbucks","amount":89.50,"category":"Kafe","currency":"TRY","isSubscription":false},
                  {"date":"2026-04-03","description":"Netflix","amount":49.9
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(truncated).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        // İlk iki tam obje kurtarılmış olmalı, yarım üçüncü atılmış olmalı
        assertThat(dtos).hasSize(2);
        assertThat(dtos.stream().map(TransactionDto::description))
                .containsExactlyInAnyOrder("Migros", "Starbucks");
    }

}

