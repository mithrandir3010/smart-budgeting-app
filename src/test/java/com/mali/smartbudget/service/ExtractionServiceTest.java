package com.mali.smartbudget.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Category;
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
 * LLM kısa schema: {"d":"YYYY-MM-DD","n":"Mağaza","a":1234.56,"t":0}
 * Kategori ve abonelik tespiti Java tarafında yapılır (detectGranularCategory / detectSubscription).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionService — Birim Testleri")
class ExtractionServiceTest {

    @Mock private ChatLanguageModel chatLanguageModel;
    @Mock private PdfService pdfService;
    @Mock private CategorizationService categorizationService;
    @Mock private MerchantCacheService merchantCacheService;

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

        // Kategorileme sabit OTHER döndürür — kategorileme CategorizationServiceTest'te ayrıca test edilir.
        org.mockito.Mockito.lenient()
                .when(categorizationService.categorize(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Category.OTHER);

        // Merchant cache testlerde varsayılan olarak boş döner (cache miss) — learn() sessizce çalışır.
        org.mockito.Mockito.lenient()
                .when(merchantCacheService.lookup(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.lenient()
                .doNothing().when(merchantCacheService)
                .learn(org.mockito.ArgumentMatchers.any(),
                       org.mockito.ArgumentMatchers.any(),
                       org.mockito.ArgumentMatchers.anyBoolean());
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
                  {"d": "2026-04-01", "n": "Test", "a": 100.00, "t": 0}
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
                [{"d":"05.04.2026","n":"Migros","a":100.00,"t":0}]
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
                [{"d":"05/04/2026","n":"Starbucks","a":89.50,"t":0}]
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
                [{"d":"2026/04/05","n":"Shell","a":450.00,"t":0}]
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
                [{"d":"5.4.2026","n":"Carrefour","a":320.00,"t":0}]
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
                [{"d":"05-04-2026","n":"BİM","a":178.00,"t":0}]
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
                  {"d":"2026-04-01","n":"ISO",   "a":10,"t":0},
                  {"d":"02.04.2026","n":"Dot",   "a":20,"t":0},
                  {"d":"03/04/2026","n":"Slash", "a":30,"t":0},
                  {"d":"2026/04/04","n":"YSlash","a":40,"t":0},
                  {"d":"5.4.2026", "n":"Short",  "a":50,"t":0},
                  {"d":"06-04-2026","n":"Dash",  "a":60,"t":0}
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
                [{"d":"2026-04-01","n":"Migros","a":"1.234,56","t":0}]
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
                [{"d":"2026-04-01","n":"Kafe","a":"89,50","t":0}]
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
                [{"d":"2026-04-01","n":"BİM","a":245,"t":0}]
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
                  {"d":null,"n":"NullDate","a":300.00,"t":0},
                  {"d":"2026-04-02","n":"Geçerli","a":100.00,"t":0}
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
                  {"d":"April 5, 2026","n":"BadDate","a":200.00,"t":0},
                  {"d":"2026-04-01","n":"Geçerli","a":500.00,"t":0}
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
                  {"d":"2026-04-01","n":"NullAmount","a":null,"t":0},
                  {"d":"2026-04-02","n":"Geçerli","a":150.00,"t":0}
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
                  {"d":"2026-04-01","n":"ZeroAmount","a":0.00,"t":0},
                  {"d":"2026-04-02","n":"Geçerli","a":250.00,"t":0}
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
                  {"d":"2026-04-01","n":"Geçerli1","a":100.00,"t":0},
                  {"d":null,        "n":"NullDate","a":200.00,"t":0},
                  {"d":"2026-04-03","n":"Geçerli2","a":300.00,"t":0},
                  {"d":"INVALID",   "n":"BadDate", "a":400.00,"t":0},
                  {"d":"2026-04-05","n":"ZeroAmt", "a":0.00,  "t":0}
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
                  {"d":null,"n":"NullDate1","a":100.00,"t":0},
                  {"d":null,"n":"NullDate2","a":200.00,"t":0}
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
    @DisplayName("null description (n) → 'Bilinmeyen' varsayılanı kullanılır")
    void extractDtos_nullDescription_defaultsToBilinmeyen() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":null,"a":100.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).description()).isEqualTo("Bilinmeyen");
    }

    @Test
    @DisplayName("Bilinmeyen mağaza adı → detectGranularCategory 'Diğer' döner")
    void extractDtos_unknownDescription_categoryDefaultsToDiger() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":"XYZ Bilinmeyen Yer","a":100.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).category()).isEqualTo("Diğer");
    }

    @Test
    @DisplayName("currency alanı yoksa 'TRY' kullanılır")
    void extractDtos_noCurrencyField_defaultsToTRY() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":"Test","a":100.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("currency alanı LLM'den gelse de her zaman 'TRY' olarak sabitlenir")
    void extractDtos_currencyAlwaysTRY() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":"Test","a":100.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("Netflix açıklaması → detectSubscription true döner")
    void extractDtos_netflixDescription_isSubscriptionTrue() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":"Netflix","a":79.99,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(json).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).isSubscription()).isTrue();
    }

    @Test
    @DisplayName("Market açıklaması → detectSubscription false döner")
    void extractDtos_marketDescription_isSubscriptionFalse() throws IOException {
        String json = """
                [{"d":"2026-04-01","n":"Migros Market","a":200.00,"t":0}]
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
        // Bu tam olarak bildirilen hatayı simüle eder: "a": 1.250.00
        String malformedJson = """
                [{"d":"2026-04-01","n":"Migros","a":1.250.00,"t":0}]
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
                [{"d":"2026-04-05","n":"Kira","a":12.480.37,"t":0}]
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
        String malformedJson = "[{\"d\":\"2026-04-01\",\"n\":\"Shell\",\"a\":1.250,00,\"t\":0}]";
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
    @DisplayName("sanitizeJson: karışık tutarlar içeren JSON'ı düzeltir (yeni 'a' anahtarı)")
    void sanitizeJson_mixedAmounts_allFixed() {
        String input = """
                [
                  {"a":1.250.00},
                  {"a":12.480,37},
                  {"a":"89,90"},
                  {"a":1500.00},
                  {"a":49.99}
                ]
                """;
        String result = extractionService.sanitizeJson(input);

        assertThat(result).contains("\"a\":1250.00");
        assertThat(result).contains("\"a\":12480.37");
        assertThat(result).contains("\"a\":89.90");
        assertThat(result).contains("\"a\":1500.00");
        assertThat(result).contains("\"a\":49.99");
        // Tırnak karakterleri kaldırılmış olmalı
        assertThat(result).doesNotContain("\"a\":\"");
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
                  {"d":"2026-04-01","n":"Migros","a":250.00,"t":0},
                  {"d":"2026-04-02","n":"Starbucks","a":89.50,"t":0},
                  {"d":"2026-04-03","n":"Netflix","a":49.9
                """;
        when(pdfService.extractText(any())).thenReturn("metin");
        doReturn(truncated).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        // İlk iki tam obje kurtarılmış olmalı, yarım üçüncü atılmış olmalı
        assertThat(dtos).hasSize(2);
        assertThat(dtos.stream().map(TransactionDto::description))
                .containsExactlyInAnyOrder("Migros", "Starbucks");
    }

    // =========================================================================
    // enrichWithInstallments — Hata 2, 3, 4 regresyon testleri
    // =========================================================================

    @Test
    @DisplayName("Hata 3: LLM yanlış taksit (t=1) atamışsa post-processor doğru (3/3) ile üzerine yazar")
    void enrichWithInstallments_wrongInstallmentByLlm_correctedByPostProcessor() throws IOException {
        String pdfText = "14 Ocak 2026 TURKCELL 412,53\n1.237,60 TL'lik işlemin 3 / 3 taksidi\n";
        String llmJson = """
                [{"d":"2026-01-14","n":"Turkcell","a":412.53,"t":1}]
                """;
        when(pdfService.extractText(any())).thenReturn(pdfText);
        doReturn(llmJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        TransactionDto t = dtos.get(0);
        assertThat(t.isInstallment()).isTrue();
        assertThat(t.currentInstallment()).isEqualTo(3);
        assertThat(t.totalInstallments()).isEqualTo(3);
    }

    @Test
    @DisplayName("Hata 2: LLM transaction'ı tamamen atladıysa post-processor yeni DTO oluşturur")
    void enrichWithInstallments_llmMissedTransaction_newDtoCreated() throws IOException {
        String pdfText = """
                14 Ocak 2026 TURKCELL 412,53
                1.237,60 TL'lik işlemin 3 / 3 taksidi
                """;
        // LLM farklı bir işlem döndürdü, TURKCELL'i tamamen atladı
        String llmJson = """
                [{"d":"2026-04-01","n":"Migros","a":250.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn(pdfText);
        doReturn(llmJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        // Migros (LLM'den) + yeni TURKCELL taksit DTO'su (post-processor'dan)
        assertThat(dtos).hasSize(2);
        TransactionDto turkcell = dtos.stream()
                .filter(TransactionDto::isInstallment)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Taksit DTO'su bulunamadı"));
        assertThat(turkcell.date()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(turkcell.amount()).isEqualByComparingTo("412.53");
        assertThat(turkcell.currentInstallment()).isEqualTo(3);
        assertThat(turkcell.totalInstallments()).isEqualTo(3);
    }

    @Test
    @DisplayName("Hata 4: TAKSİTLENDİRME FAİZİ satırı i-1'e girince i-2 fallback devreye girer")
    void enrichWithInstallments_faizLineBetween_i2FallbackWorks() throws IOException {
        // FAİZİ satırı transaction ile taksidi satırı arasına sıkışmış
        String pdfText = """
                17 Mart 2026 TURKCELL 689,96 1.379,94 / 2
                TAKSİTLENDİRME ISLEMI 87,45
                2.069,90 TL'lik işlemin 1 / 3 taksidi
                """;
        String llmJson = """
                [{"d":"2026-03-17","n":"Turkcell","a":689.96,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn(pdfText);
        doReturn(llmJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        assertThat(dtos).hasSize(1);
        TransactionDto t = dtos.get(0);
        assertThat(t.isInstallment()).isTrue();
        assertThat(t.currentInstallment()).isEqualTo(1);
        assertThat(t.totalInstallments()).isEqualTo(3);
    }

    @Test
    @DisplayName("Gerçek Yapı Kredi formatı: LLM 1 transaction döndürdü, 2 taksit atladı → toplam 3 DTO")
    void enrichWithInstallments_realYapiKrediFormat_missingInstallmentsCreated() throws IOException {
        String pdfText = """
                14 Ocak 2026 TURKCELL 412,53
                1.237,60 TL'lik işlemin 3 / 3 taksidi
                23 Ocak 2026 İYZİCO/ERCAN CANDAN İSTANBUL TR 400,00 1.200,00 / 3
                2.400,00 TL'lik işlemin 3 / 6 taksidi
                07 Mart 2026 TODTV.COM.TR ISTANBUL TR 179,00 1.969,00 / 11 215
                2.148,00 TL'lik işlemin 1 / 12 taksidi
                """;
        // LLM sadece TODTV'yi yakaladı, TURKCELL ve İYZİCO'yu atladı
        String llmJson = """
                [{"d":"2026-03-07","n":"Todtv.com.tr","a":179.00,"t":0}]
                """;
        when(pdfService.extractText(any())).thenReturn(pdfText);
        doReturn(llmJson).when(chatLanguageModel).generate(anyString());

        List<TransactionDto> dtos = extractionService.extractDtos(dummyFile);

        // TODTV güncellendi + 2 yeni DTO (TURKCELL + İYZİCO)
        assertThat(dtos).hasSize(3);

        TransactionDto todtv = dtos.stream()
                .filter(d -> d.description().toLowerCase().contains("todtv"))
                .findFirst().orElseThrow();
        assertThat(todtv.isInstallment()).isTrue();
        assertThat(todtv.currentInstallment()).isEqualTo(1);
        assertThat(todtv.totalInstallments()).isEqualTo(12);

        TransactionDto turkcell = dtos.stream()
                .filter(d -> "TURKCELL".equalsIgnoreCase(d.description()))
                .findFirst().orElseThrow();
        assertThat(turkcell.isInstallment()).isTrue();
        assertThat(turkcell.currentInstallment()).isEqualTo(3);
        assertThat(turkcell.totalInstallments()).isEqualTo(3);

        TransactionDto iyzico = dtos.stream()
                .filter(d -> d.description().toUpperCase().contains("İYZİCO")
                          || d.description().toUpperCase().contains("IYZICO"))
                .findFirst().orElseThrow();
        assertThat(iyzico.isInstallment()).isTrue();
        assertThat(iyzico.currentInstallment()).isEqualTo(3);
        assertThat(iyzico.totalInstallments()).isEqualTo(6);
    }

    // =========================================================================
    // detectGranularCategory — birim testleri
    // =========================================================================

    @Test
    @DisplayName("detectGranularCategory: Migros → Market")
    void detectGranularCategory_migros_returnsMarket() {
        assertThat(extractionService.detectGranularCategory("Migros")).isEqualTo("Market");
        assertThat(extractionService.detectGranularCategory("BİM")).isEqualTo("Market");
        assertThat(extractionService.detectGranularCategory("A101 Market")).isEqualTo("Market");
    }

    @Test
    @DisplayName("detectGranularCategory: Starbucks → Kafe")
    void detectGranularCategory_starbucks_returnsKafe() {
        assertThat(extractionService.detectGranularCategory("Starbucks")).isEqualTo("Kafe");
        assertThat(extractionService.detectGranularCategory("Kahve Dünyası")).isEqualTo("Kafe");
    }

    @Test
    @DisplayName("detectGranularCategory: McDonald's → Restoran")
    void detectGranularCategory_mcdonalds_returnsRestoran() {
        assertThat(extractionService.detectGranularCategory("McDonald's")).isEqualTo("Restoran");
        assertThat(extractionService.detectGranularCategory("Burger King")).isEqualTo("Restoran");
    }

    @Test
    @DisplayName("detectGranularCategory: Shell → Akaryakıt")
    void detectGranularCategory_shell_returnsAkaryakit() {
        assertThat(extractionService.detectGranularCategory("Shell")).isEqualTo("Akaryakıt");
        assertThat(extractionService.detectGranularCategory("Opet Petrol")).isEqualTo("Akaryakıt");
    }

    @Test
    @DisplayName("detectGranularCategory: Netflix → Eğlence")
    void detectGranularCategory_netflix_returnsEglence() {
        assertThat(extractionService.detectGranularCategory("Netflix")).isEqualTo("Eğlence");
        assertThat(extractionService.detectGranularCategory("Spotify")).isEqualTo("Eğlence");
    }

    @Test
    @DisplayName("detectGranularCategory: Turkcell → Fatura")
    void detectGranularCategory_turkcell_returnsFatura() {
        assertThat(extractionService.detectGranularCategory("Turkcell")).isEqualTo("Fatura");
    }

    @Test
    @DisplayName("detectGranularCategory: bilinmeyen → Diğer")
    void detectGranularCategory_unknown_returnsDiger() {
        assertThat(extractionService.detectGranularCategory("XYZ Bilinmeyen Yer")).isEqualTo("Diğer");
        assertThat(extractionService.detectGranularCategory("")).isEqualTo("Diğer");
        assertThat(extractionService.detectGranularCategory(null)).isEqualTo("Diğer");
    }

    // =========================================================================
    // detectSubscription — birim testleri
    // =========================================================================

    @Test
    @DisplayName("detectSubscription: Netflix/Spotify/Disney → true")
    void detectSubscription_knownServices_returnsTrue() {
        assertThat(extractionService.detectSubscription("Netflix")).isTrue();
        assertThat(extractionService.detectSubscription("Spotify")).isTrue();
        assertThat(extractionService.detectSubscription("Disney+")).isTrue();
        assertThat(extractionService.detectSubscription("YouTube Premium")).isTrue();
        assertThat(extractionService.detectSubscription("iCloud")).isTrue();
        assertThat(extractionService.detectSubscription("Adobe Creative Cloud")).isTrue();
    }

    @Test
    @DisplayName("detectSubscription: Market/Restoran → false")
    void detectSubscription_nonSubscription_returnsFalse() {
        assertThat(extractionService.detectSubscription("Migros")).isFalse();
        assertThat(extractionService.detectSubscription("Turkcell")).isFalse();
        assertThat(extractionService.detectSubscription("Shell")).isFalse();
        assertThat(extractionService.detectSubscription(null)).isFalse();
    }
}
