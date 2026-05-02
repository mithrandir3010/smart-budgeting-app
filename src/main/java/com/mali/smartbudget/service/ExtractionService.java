package com.mali.smartbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.util.AmountNormalizer;
import com.mali.smartbudget.util.PdfTextCleaner;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.util.StopWatch;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF ekstreden işlem (Transaction) verilerini LLM aracılığıyla ayıklayan servis.
 *
 * <h3>Pipeline</h3>
 * <pre>
 *  PDF bytes
 *    └─ PdfService.extractText()       → ham metin
 *    └─ PdfTextCleaner.clean()         → gürültüsüz metin   [pre-processing]
 *    └─ LLM (GPT-4o-mini)              → JSON string
 *    └─ cleanJson()                    → markdown bloğu temizleme
 *    └─ parseRowFaultTolerant()        → List<TransactionDto> (hatalı satırlar atlanır)
 *       └─ AmountNormalizer.normalize()→ BigDecimal          [tutar normalizasyonu]
 *    └─ TransactionService.saveAll()   → DB
 * </pre>
 *
 * <h3>Fault-Tolerant Parsing</h3>
 * LLM'den dönen JSON içindeki tek bir bozuk satır tüm süreci patlatmaz.
 * Her satır bağımsız parse edilir; başarısız satırlar loglanıp atlanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    /**
     * Türk bankası ekstrelerine özel LLM prompt'u.
     * Kısa schema {d,n,a,t} → ~95% daha az prompt token.
     * Kategori ve abonelik tespiti Java tarafında yapılır (detectGranularCategory / detectSubscription).
     */
    private static final String PROMPT_TEMPLATE = """
            Turkish bank statement below. Extract spending transactions → ONLY a JSON array.
            Start with [ end with ]. No markdown fences, no explanation.

            Schema — each object exactly:
            {"d":"YYYY-MM-DD","n":"Merchant Name","a":1234.56,"t":0}

            d = date ISO 8601 (15.03.2026 → "2026-03-15", 15/03/2026 → "2026-03-15")
            n = merchant name only; strip POS ID/ref/terminal/branch/city suffix; fix casing; keep ğşıçöü; MKT→Market, REST→Restoran, PETROL→Akaryakıt
            a = amount as plain number, dot-decimal only
                Turkish format:  1.250,00 TL → 1250.00 | 89,90 → 89.90
                English format:  1,100.00 → 1100.00 | 793.55 → 793.55
                WRONG: 1.250.00  WRONG: 1.250,00  WRONG: "1250.00"  RIGHT: 1250.00
            t = 0 for normal transactions.
                İş Bankası taksit: "512,23 2/6 taksidi (3.073,40)" → t=2 (N/M taksidi → t=N)
                Halkbank taksit:   "490.23  1960.94/6-02.Taksit" → t=2
                Yapı Kredi taksit: next line "X TL'lik işlemin N / M taksidi" → t=N

            Skip: balance rows, IBAN transfers, page headers/footers, fee-only lines (FATURA ÖDEME ÜCRETI),
                  account payment rows (HESAPTAN AKTARIM, Hesaptan Ödeme), refunds (+prefix),
                  rows containing "TAKSİTLENDİRME İŞLEM FAİZİ". Max 150 transactions.

            --- Yapı Kredi (WorldCard) examples — date "DD MonthName YYYY", Turkish decimal, city+TR/TU then amount, trailing int = puan (ignore) ---
            "06 Mart 2026 DRAGON SARKUTERI VE TURISTANBUL TU 800,00" → {"d":"2026-03-06","n":"Dragon Sarküteri","a":800.00,"t":0}
            "06 Mart 2026 KADIKÖY İSTANBUL TR 390,00 6" → {"d":"2026-03-06","n":"Kadıköy","a":390.00,"t":0}
            "14 Ocak 2026 TURKCELL 412,53\\n1.237,60 TL'lik işlemin 3 / 3 taksidi" → {"d":"2026-01-14","n":"Turkcell","a":412.53,"t":3}
            "14 Mart 2026 AVIS.COM.TR ISTANBUL TR 2.501,15 12.505,75 / 5 1.501\\n15.006,90 TL'lik işlemin 1 / 6 taksidi" → {"d":"2026-03-14","n":"Avis","a":2501.15,"t":1}
            "09 Mart 2026 ÖDEME-İNTERNET BANKACILIĞI +26.237,80" → SKIP (payment row)
            "17 Mart 2026 TAKSİTLENDİRME İŞLEM FAİZİ 87,45" → SKIP (interest fee, not a merchant)

            --- Halkbank (Paraf) examples — date DD/MM/YYYY, amount English decimal, trailing city ---
            "14/03/2026 BAYRAMPAŞA MİGROS İSTANBUL 793.55  1.98" → {"d":"2026-03-14","n":"Migros","a":793.55,"t":0}
            "30/03/2026 GOOGLE *YouTubePremI LONDON 60.65  0.00" → {"d":"2026-03-30","n":"YouTube Premium","a":60.65,"t":0}
            "01/03/2026 İYZİCO /AMAZON.COM.T ISTANBUL 490.23  1960.94/6-\\n02.Taksit 0.00" → {"d":"2026-03-01","n":"Amazon","a":490.23,"t":2}
            "21/03/2026 MOOD TRİO BEACH AYDIN 1,100.00  0.00" → {"d":"2026-03-21","n":"Mood Trio Beach","a":1100.00,"t":0}
            "20/03/2026 Hesaptan Ödeme - Teşekkür Ederiz - + 8,925.44  0.00" → SKIP (payment row)

            --- İş Bankası (Maximum) examples — date DD/MM/YYYY, amount Turkish decimal, CITY TR suffix ---
            "12/03/2026 TARIHI IZNIK FIRINI BURSA TR 70,00" → {"d":"2026-03-12","n":"Tarihi İznik Fırını","a":70.00,"t":0}
            "24/03/2026 SURA AKARYAKIT BURSA TR 1.000,00" → {"d":"2026-03-24","n":"Sura Akaryakıt","a":1000.00,"t":0}
            "14/03/2026 IYZICO/AMAZON TURKEY PE ISTANBUL TR 512,23 2/6 taksidi (3.073,40)" → {"d":"2026-03-14","n":"Amazon","a":512.23,"t":2}
            "27/03/2026 WWW. GIB.GOV.TR BURSA TR 953,16 3/3 taksidi (2.859,50)" → {"d":"2026-03-27","n":"GIB Vergi","a":953.16,"t":3}
            "12/03/2026 IGDAS-ISTANBUL GAZ DAGI FATURA ÖDEME ÜCRETI 31,16" → SKIP (service fee)
            "20/03/2026 2225-392377 HESAPTAN AKTARIM 2225 İNTERAKTİF -14.851,98" → SKIP (payment)

            Statement:
            %s
            """;

    /**
     * Tek bir chunk'ın taşıyabileceği maksimum karakter sayısı (~9 000 token).
     * Bir chunk bu sınırı aşarsa uyarı loglanır ama yine de gönderilir.
     */
    private static final int MAX_INPUT_CHARS = 60_000;

    /** Her chunk'taki satır sayısı. 100 satır ≈ 20-30 işlem. */
    private static final int LINES_PER_CHUNK = 100;

    /**
     * Chunk sınırlarında taksit bilgisinin bölünmesini engelleyen örtüşme satırı sayısı.
     * Önceki chunk'ın son 3 satırı bir sonraki chunk'ın başına eklenir.
     */
    private static final int CHUNK_OVERLAP_LINES = 3;

    // Tarih formatları (LLM bazen farklı format döner — tümünü destekle)
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2026-04-05
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),          // 05.04.2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),          // 05/04/2026
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),          // 2026/04/05
            DateTimeFormatter.ofPattern("d.M.yyyy"),            // 5.4.2026
            DateTimeFormatter.ofPattern("dd-MM-yyyy")           // 05-04-2026
    );

    /**
     * "amount" alanındaki sayı değerini yakalar — hem tırnaklı string hem de
     * bare number formlarında çalışır.
     *
     * <p>Sayı grubu ({@code \\d[\\d.]*(?:,\\d+)?}) kasıtlı olarak tasarlanmıştır:
     * <ul>
     *   <li>{@code \\d[\\d.]*} — zorunlu rakam, ardından rakam/nokta (binlik noktalara izin verir)</li>
     *   <li>{@code (?:,\\d+)?} — isteğe bağlı {@code ,XX} son eki (Türkçe ondalık virgül)</li>
     * </ul>
     * Bu kombinasyon JSON ayırıcı virgülünü ({@code ,} sonrasında {@code "} veya başka karakter) yakalamazken
     * Türk tutar virgülünü ({@code ,} sonrasında rakam) yakalar.
     *
     * <p>Grup referansları:
     * <ol>
     *   <li>key + kolon + boşluklar: {@code "amount":}</li>
     *   <li>opsiyonel açılış tırnağı</li>
     *   <li>ham sayı (rakam, nokta, ve opsiyonel sonunda virgül+rakam)</li>
     *   <li>opsiyonel kapanış tırnağı</li>
     * </ol>
     */
    private static final Pattern AMOUNT_JSON_PATTERN = Pattern.compile(
            "(\"a\"\\s*:\\s*)(\"?)(\\d[\\d.]*(?:,\\d+)?)(\"?)",
            Pattern.CASE_INSENSITIVE
    );

    // ── Kural tabanlı taksit post-processor pattern'ları ─────────────────────
    /** "1.237,60 TL'lik işlemin 3 / 3 taksidi" — group1=toplam tutar, group2=N, group3=M */
    private static final Pattern TAKSIT_SUBLINE_FULL = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s+TL.lik\\s+i\\u015flemin\\s+(\\d{1,2})\\s*/\\s*(\\d{1,3})\\s+taksidi",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── Transaction Router pattern'ları ──────────────────────────────────────

    /**
     * Standart tek-satır Türk banka formatı: dd.MM.yyyy AÇIKLAMA TUTAR[,CC] [TL]
     * HIGH confidence kontrolünün ilk geçidi.
     */
    private static final Pattern HIGH_CONF_LINE = Pattern.compile(
            "^(\\d{1,2}\\.\\d{1,2}\\.\\d{4})\\s+(\\S.*?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s+TL)?\\s*$"
    );

    /** "X TL'lik işlemin N / M taksidi" — kesin taksit alt satırı işareti. */
    private static final Pattern TAKSIT_SUBLINE_MARKER = Pattern.compile(
            "TL.lik\\s+i\\u015flemin", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Aynı satırda taksit sayacı: "400,00 / 3" — karmaşık satır işareti. */
    private static final Pattern TAKSIT_COUNT_IN_LINE = Pattern.compile(",\\d{2}\\s*/\\s*\\d+");

    /** POS terminal / referans kodu: açıklamada 6+ rakam → LOW confidence. */
    private static final Pattern POS_CODE = Pattern.compile("\\d{6,}");

    /** Kalan taksit sayısı suffix: "/ 11", "/ 2" */
    private static final Pattern REMAINING_COUNT = Pattern.compile("/\\s*\\d{1,3}\\b");

    /** Ülke/şebeke kodu: satır içindeki " TR", " TU" */
    private static final Pattern COUNTRY_CODE = Pattern.compile("(?<=\\s)(TR|TU)(?=\\s|$)");

    /** Satır sonundaki puan değerleri (215, 775, 1501 gibi) */
    private static final Pattern TRAILING_INT = Pattern.compile("\\s+\\d{1,4}\\s*$");

    /** "14 Ocak 2026", "7 Mart 2026" — Türkçe ay adıyla tarih */
    private static final Pattern TURKISH_DATE_IN_LINE = Pattern.compile(
            "\\b(\\d{1,2})\\s+(ocak|\\u015fubat|mart|nisan|may\\u0131s|haziran|" +
            "temmuz|a\\u011fustos|eyl\\u00fcl|ekim|kas\\u0131m|aral\\u0131k)\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Türk formatı tutar: "412,53", "1.765,83", "2.501,15" */
    private static final Pattern TURKISH_AMOUNT_IN_LINE = Pattern.compile(
            "\\b(\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\b"
    );

    /** İngiliz/US formatı tutar: "490.23", "1,100.00", "6,750.00" (Halkbank) */
    private static final Pattern ENGLISH_AMOUNT_IN_LINE = Pattern.compile(
            "\\b(\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\b"
    );

    // ── Banka tipine özgü HIGH CONF pattern'ları ──────────────────────────────

    /**
     * Halkbank (Paraf): DD/MM/YYYY AÇIKLAMA(+ŞEHİR) ENG_TUTAR  PARAFPARA
     * Örnek: "14/03/2026 BAYRAMPAŞA MİGROS İSTANBUL 793.55  1.98"
     */
    private static final Pattern HIGH_CONF_HALKBANK = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(.+)\\s+(\\d{1,3}(?:,\\d{3})*\\.\\d{2})\\s{2,}\\d+\\.\\d{2}\\s*$"
    );

    /**
     * İş Bankası (Maximum): DD/MM/YYYY AÇIKLAMA ŞEHİR TR TÜRK_TUTAR [MAXIPUAN]
     * Örnek: "11/03/2026 IDRIS DERE BURSA TR 150,00"
     * Örnek: "11/03/2026 BIM AS T004 TOPKAPI / I BURSA TR 309,52 0,05"
     */
    private static final Pattern HIGH_CONF_ISBANK = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{4})\\s+(.+)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s+\\d{1,3},\\d{2})?\\s*$"
    );

    /**
     * Yapı Kredi (WorldCard): "DD MonthName YYYY AÇIKLAMA [ŞEHİR TR/TU] TUTAR [PUAN]"
     * Örnek: "06 Mart 2026 MİGROS KADIKÖY İSTANBUL TR 220,00"
     * Örnek: "17 Mart 2026 IPEK KESTANE BURSA TR 2.850,00 855"  (855 = puan)
     */
    private static final Pattern HIGH_CONF_YAPIKREDI = Pattern.compile(
            "^(\\d{2}\\s+(?:ocak|\\u015fubat|mart|nisan|may\\u0131s|haziran|" +
            "temmuz|a\\u011fustos|eyl\\u00fcl|ekim|kas\\u0131m|aral\\u0131k)\\s+\\d{4})" +
            "\\s+(.+?)\\s+(\\d{1,3}(?:\\.\\d{3})*,\\d{2})(?:\\s+\\d{1,5})?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** İş Bankası taksit bilgisi: "2/6 taksidi (3.073,40)" aynı satırda */
    private static final Pattern ISBANK_TAKSIT_IN_LINE = Pattern.compile(
            "\\d+/\\d+\\s+taksidi", Pattern.CASE_INSENSITIVE
    );

    /** İş Bankası/Halkbank tarih: DD/MM/YYYY ile başlayan satır */
    private static final Pattern SLASH_DATE_LINE = Pattern.compile(
            "^\\d{2}/\\d{2}/\\d{4}\\s+"
    );

    /** Halkbank taksit referans suffix: "1960.94/6-" gibi satır sonu ifadeleri */
    private static final Pattern HALKBANK_TAKSIT_REF = Pattern.compile(
            "\\d+\\.\\d+/\\d+-\\s*$"
    );
    /** Halkbank taksit sıra numarası satırı: "02.Taksit 0.00" */
    private static final Pattern HALKBANK_TAKSIT_NUM = Pattern.compile(
            "^0?\\d\\.Taksit\\s"
    );

    private static final Map<String, Integer> TURKISH_MONTHS = Map.ofEntries(
            Map.entry("ocak", 1),   Map.entry("şubat", 2),  Map.entry("mart", 3),
            Map.entry("nisan", 4),  Map.entry("mayıs", 5),  Map.entry("haziran", 6),
            Map.entry("temmuz", 7), Map.entry("ağustos", 8), Map.entry("eylül", 9),
            Map.entry("ekim", 10),  Map.entry("kasım", 11), Map.entry("aralık", 12)
    );

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final ObjectMapper objectMapper;
    private final CategorizationService categorizationService;
    private final MerchantCacheService merchantCacheService;

    // ─────────────────────────────────────────────────────────────────────────
    // Başlangıç doğrulaması
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        if (openAiApiKey == null || openAiApiKey.isBlank() || !openAiApiKey.startsWith("sk-")) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY geçersiz veya eksik! " +
                    "Proje kök dizinindeki .env dosyasında OPENAI_API_KEY=sk-... tanımını kontrol edin.");
        }
        log.info(">>> OpenAI service is now ACTIVE with real API key: {}...",
                openAiApiKey.substring(0, Math.min(10, openAiApiKey.length())));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM çağrısı — tek chunk
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Verilen chunk metni için LLM çağrısı yapar ve ham JSON yanıtını döner.
     * Chunk'lara bölme ve deduplication {@link #extractDtos} tarafından yönetilir.
     */
    private String callLlmForChunk(String chunkText, int chunkIdx, int totalChunks) {
        if (chunkText.length() > MAX_INPUT_CHARS) {
            log.warn("[chunk-{}/{}] Chunk {} karakter — limit {}. Yine de gönderiliyor.",
                    chunkIdx, totalChunks, chunkText.length(), MAX_INPUT_CHARS);
        }
        String prompt = String.format(PROMPT_TEMPLATE, chunkText);
        log.info("[chunk-{}/{}] LLM isteği gönderiliyor... (~{} tahmini token | {} satır)",
                chunkIdx, totalChunks, prompt.length() / 4, countLines(chunkText));

        StopWatch llmSw = new StopWatch();
        llmSw.start();
        String jsonResponse = chatLanguageModel.generate(prompt);
        llmSw.stop();
        log.info("[chunk-{}/{}] LLM yanıtı alındı. {} karakter | {}ms",
                chunkIdx, totalChunks, jsonResponse.length(), llmSw.getTotalTimeMillis());
        log.debug("[chunk-{}/{}] LLM ham yanıt:\n{}", chunkIdx, totalChunks, jsonResponse);
        return jsonResponse;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ana iş akışı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PDF'ten harcama DTO'larını ayıklar — saf I/O, hiç DB işlemi yapmaz.
     *
     * <p>PDF ONCE okunur, temizlenir, {@value #LINES_PER_CHUNK}-satırlık chunk'lara bölünür.
     * Her chunk için ayrı LLM çağrısı yapılır; sonuçlar (date+description+amount) anahtarıyla
     * tekilleştirilir. {@link #enrichWithInstallments} için PDF ikinci kez okunmaz — aynı
     * temiz metin yeniden kullanılır.
     *
     * @param file Kullanıcının yüklediği PDF ekstre dosyası
     * @return Parse edilmiş TransactionDto listesi (boş olamaz — exception fırlar)
     * @throws IOException PDF okunamazsa
     * @throws IllegalArgumentException Geçerli işlem bulunamazsa
     */
    public List<TransactionDto> extractDtos(MultipartFile file) throws IOException {
        StopWatch pipelineSw = new StopWatch("ExtractionPipeline");
        pipelineSw.start("full-pipeline");

        // ── [1/5] PDF'ten ham metin & temizleme ─────────────────────────────
        log.info(">>> Extraction mode: LLM (chunked) | apiKey: {}...",
                openAiApiKey.substring(0, Math.min(10, openAiApiKey.length())));
        String rawText = pdfService.extractText(file);
        log.info("[1/5] PDF okundu. Ham metin: {} karakter", rawText.length());

        if (rawText.isBlank()) {
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. PDF metin içermiyor; " +
                    "taranmış (image-only) veya şifreli bir PDF olabilir.");
        }
        log.debug("[1/5] Ham metin (ilk 800 karakter):\n{}",
                rawText.substring(0, Math.min(800, rawText.length())));

        String cleanText = PdfTextCleaner.clean(rawText);
        log.info("[1/5] PdfTextCleaner tamamlandı. Temiz metin: {} karakter", cleanText.length());

        if (cleanText.isBlank()) {
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. Temizleme sonrası işlenebilir metin kalmadı.");
        }
        log.debug("[1/5] Temiz metin (ilk 800 karakter):\n{}",
                cleanText.substring(0, Math.min(800, cleanText.length())));

        // ── [2/5] Transaction Router — HIGH confidence → LOCAL (0 token), LOW → LLM ──
        BankType bankType = detectBankType(rawText);
        log.info("[router] Tespit edilen banka: {}", bankType);

        String[]             allLines  = cleanText.split("\n", -1);
        List<TransactionDto> localDtos = new ArrayList<>();
        List<String>         llmLines  = new ArrayList<>();

        // Halkbank: "Sonradan Taksit MERCHANT X." satırlarını tara — orijinal
        // ücret aynı ekstreden aynı gün krediyle iade edildiğinden (SonradanTak.Alacak)
        // sadece taksit tutarı kalır; orijinal satırı LLM'e göndermemek çifte sayımı önler.
        Set<String> sonradanTaksitMerchants = new java.util.HashSet<>();
        if (bankType == BankType.HALKBANK) {
            for (String raw : allLines) {
                String t = raw.trim().replace('İ', 'i').toLowerCase(Locale.ROOT);
                int idx = t.indexOf("sonradan taksit");
                if (idx < 0) continue;
                String rest = t.substring(idx + "sonradan taksit".length()).trim();
                // strip leading DD/MM/YYYY prefix if any
                rest = rest.replaceFirst("^\\d{2}/\\d{2}/\\d{4}\\s+", "");
                StringBuilder sb = new StringBuilder();
                for (String w : rest.split("\\s+")) {
                    if (w.matches("\\d.*") || w.equals("taksit")) break;
                    sb.append(w).append(' ');
                }
                String merchant = sb.toString().trim();
                if (!merchant.isEmpty()) sonradanTaksitMerchants.add(merchant);
            }
            if (!sonradanTaksitMerchants.isEmpty()) {
                log.debug("[router] Sonradan Taksit tespit edilen merchant(lar): {}", sonradanTaksitMerchants);
            }
        }

        for (int i = 0; i < allLines.length; i++) {
            String line     = allLines[i].trim();
            String nextLine = (i + 1 < allLines.length) ? allLines[i + 1].trim() : "";
            if (line.isBlank()) continue;

            // Halkbank: Sonradan Taksit orijinal ücret satırını her iki yoldan da atla
            // (hem HIGH_CONF hem de LLM'den önce — merchant cache öğrenilmiş olsa bile)
            if (bankType == BankType.HALKBANK && !sonradanTaksitMerchants.isEmpty()) {
                String lower = line.replace('İ', 'i').toLowerCase(Locale.ROOT);
                if (SLASH_DATE_LINE.matcher(line).find()
                        && ENGLISH_AMOUNT_IN_LINE.matcher(line).find()
                        && !lower.contains("sonradan taksit")
                        && sonradanTaksitMerchants.stream().anyMatch(lower::contains)) {
                    log.debug("[router] Sonradan Taksit orijinal satır atlandı: {}", line);
                    continue;
                }
            }

            if (isHighConfidence(line, nextLine, bankType)) {
                Optional<TransactionDto> local = parseLineLocally(line, bankType);
                if (local.isPresent()) {
                    localDtos.add(local.get());
                    continue;
                }
                // local parse failed despite high confidence → fall through to LLM
            }

            if (isTransactionCandidate(line, bankType)) {
                llmLines.add(line);
                // Yapı Kredi "X TL'lik işlemin N/M taksidi" alt satırını birlikte gönder
                if (TAKSIT_SUBLINE_MARKER.matcher(nextLine).find()) {
                    llmLines.add(nextLine);
                    i++;
                } else if (bankType == BankType.HALKBANK) {
                    // Halkbank multi-line: tarih satırında tutar yok → sonraki satırı ekle
                    if (SLASH_DATE_LINE.matcher(line).find()
                            && !ENGLISH_AMOUNT_IN_LINE.matcher(line).find()
                            && !TURKISH_AMOUNT_IN_LINE.matcher(line).find()
                            && !nextLine.isBlank()) {
                        llmLines.add(nextLine);
                        i++;
                    }
                    // Halkbank taksit: "490.23  1960.94/6-" → "02.Taksit 0.00"
                    else if (HALKBANK_TAKSIT_REF.matcher(line).find()
                            && HALKBANK_TAKSIT_NUM.matcher(nextLine).find()) {
                        llmLines.add(nextLine);
                        i++;
                    }
                }
            }
        }
        log.info("[router] LOCAL: {} satır (0 token) | LLM aday: {} satır.",
                localDtos.size(), llmLines.size());

        Set<String>          seen    = new HashSet<>();
        List<TransactionDto> allDtos = new ArrayList<>(localDtos);
        for (TransactionDto dto : localDtos) seen.add(dtoKey(dto));
        int llmDtoCount = 0;

        // LLM'e gönderilecek metin: aday satırlar varsa onlar, yoksa tam metin fallback
        final String llmText;
        final boolean runLlm;
        if (!llmLines.isEmpty()) {
            llmText = String.join("\n", llmLines);
            runLlm  = true;
        } else if (localDtos.isEmpty()) {
            // Hiç aday bulunamadı — olağandışı format, tam metin LLM'e
            llmText = cleanText;
            runLlm  = true;
            log.info("[router] Hiç aday bulunamadı — tam metin LLM'e gönderiliyor (fallback).");
        } else {
            llmText = "";
            runLlm  = false;
            log.info("[router] Tüm satırlar yerel olarak çözüldü — LLM çağrısı atlandı.");
        }

        if (runLlm) {
            List<String> chunks = splitIntoChunks(llmText, LINES_PER_CHUNK);
            log.info("[2/5] LLM: {} chunk ({} satır/chunk, {} satır örtüşme).",
                    chunks.size(), LINES_PER_CHUNK, CHUNK_OVERLAP_LINES);

            for (int i = 0; i < chunks.size(); i++) {
                String chunk    = chunks.get(i);
                int    chunkIdx = i + 1;
                log.info("[chunk-{}/{}] LLM gönderiliyor... {} satır",
                        chunkIdx, chunks.size(), countLines(chunk));

                String               rawJson   = callLlmForChunk(chunk, chunkIdx, chunks.size());
                List<TransactionDto> chunkDtos = parseChunk(rawJson, chunkIdx);

                int added = 0;
                for (TransactionDto dto : chunkDtos) {
                    if (seen.add(dtoKey(dto))) {
                        allDtos.add(dto);
                        added++;
                        llmDtoCount++;
                    }
                }
                log.info("[chunk-{}/{}] {} işlem, {} yeni ({} mükerrer atlandı).",
                        chunkIdx, chunks.size(), chunkDtos.size(), added, chunkDtos.size() - added);
            }
        }

        log.info("[router] Özet: {} LOCAL (0 token) + {} LLM (ücretli) = {} toplam benzersiz DTO.",
                localDtos.size(), llmDtoCount, allDtos.size());

        if (allDtos.isEmpty()) {
            log.error("[2/5] Hiçbir geçerli işlem çıkarılamadı.");
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. PDF'te tanınan bir harcama işlemi bulunamadı. " +
                    "Lütfen geçerli bir banka ekstresi yüklediğinizden emin olun.");
        }

        // ── [3/5] Kural tabanlı taksit post-processor ───────────────────────
        log.info("[3/5] Kural tabanlı taksit post-processor başlıyor...");
        try {
            allDtos = enrichWithInstallments(allDtos, cleanText);
        } catch (Exception e) {
            log.warn("[3/5] Post-processor başarısız, atlanıyor: {}", e.getMessage());
        }

        // ── [4/5] Merchant cache zenginleştirme + kategorileme ───────────────
        log.info("[4/5] Merchant cache zenginleştirme başlıyor...");
        List<TransactionDto> enriched = new ArrayList<>();
        long cacheHits = 0;

        for (TransactionDto dto : allDtos) {
            String  finalCategory     = dto.category();
            boolean finalSubscription = dto.isSubscription();

            try {
                java.util.Optional<MerchantCacheService.CachedResult> cached =
                        merchantCacheService.lookup(dto.description());

                if (cached.isPresent()) {
                    finalCategory     = cached.get().category();
                    finalSubscription = cached.get().isSubscription();
                    cacheHits++;
                } else {
                    merchantCacheService.learn(dto.description(), finalCategory, finalSubscription);
                }
            } catch (Exception e) {
                log.warn("[cache] Merchant cache hatası '{}' — Java tespiti kullanılıyor: {}",
                        dto.description(), e.getMessage());
            }

            com.mali.smartbudget.model.Category categoryEnum =
                    categorizationService.categorize(dto.description(), finalCategory);

            enriched.add(new TransactionDto(
                    dto.date(), dto.description(), dto.amount(),
                    finalCategory, dto.currency(), finalSubscription,
                    dto.isInstallment(), dto.currentInstallment(), dto.totalInstallments(),
                    categoryEnum
            ));
        }

        allDtos = enriched;
        int  total  = allDtos.size();
        long hitPct = total == 0 ? 0 : Math.round(100.0 * cacheHits / total);
        log.info("[cache] {}/{} işlem merchant cache'den çözüldü (hit oranı: %{})",
                cacheHits, total, hitPct);
        if (total > 0 && cacheHits == total) {
            log.info("[cache] Tüm işlemler cache'de bulundu! " +
                     "OpenAI yalnızca yeni merchant'lar için kullanıldı.");
        }

        // ── [5/5] Tamamlandı ─────────────────────────────────────────────────
        log.info("[5/5] {} DTO kategorize edildi.", total);
        pipelineSw.stop();
        log.info("[pipeline] Extraction tamamlandı. {} DTO | toplam süre={}ms",
                allDtos.size(), pipelineSw.getTotalTimeMillis());
        return allDtos;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fault-Tolerant JSON Parser
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LLM'den dönen JSON dizisini satır satır parse eder.
     *
     * <p>Her satır bağımsız try-catch içinde işlenir.
     * Bozuk / eksik satırlar atlanır, geçerliler toplanır.
     * Böylece tek bir sorunlu kayıt tüm yükleme işlemini iptal etmez.
     *
     * <p>Uygulanan normalizasyonlar:
     * <ul>
     *   <li>Tutar → {@link AmountNormalizer#normalize(Object)}</li>
     *   <li>Tarih → çoklu format desteği ({@link #parseDate(String)})</li>
     *   <li>Para birimi → boşsa "TRY" varsayılanı</li>
     * </ul>
     *
     * @param json LLM'den gelen JSON dizisi string'i
     * @return Geçerli TransactionDto listesi
     */
    private List<TransactionDto> parseRowsFaultTolerant(String json) {
        List<Map<String, Object>> rawRows;
        try {
            rawRows = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[3/4] JSON tamamen parse edilemedi. Detay: {}\nGelen metin:\n{}",
                    e.getOriginalMessage(), json);
            throw new IllegalArgumentException(
                    "LLM geçerli bir JSON döndürmedi: " + e.getOriginalMessage(), e);
        }

        List<TransactionDto> valid   = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < rawRows.size(); i++) {
            Map<String, Object> row = rawRows.get(i);
            try {
                TransactionDto dto = mapRow(row, i);
                if (dto != null) valid.add(dto);
                else             skipped++;
            } catch (Exception e) {
                skipped++;
                log.warn("[3/4] Satır {} atlandı — {} | Satır içeriği: {}",
                        i, e.getMessage(), row);
            }
        }

        if (skipped > 0) {
            log.warn("[3/4] {} satır atlandı (toplam {}). Geçerli: {}",
                    skipped, rawRows.size(), valid.size());
        }
        return valid;
    }

    /**
     * Tek bir JSON satırını (map) {@link TransactionDto}'ya dönüştürür.
     * Kısa schema: {d, n, a, t}
     *
     * @return Geçerli DTO; zorunlu alan eksikse {@code null}
     */
    private TransactionDto mapRow(Map<String, Object> row, int index) {
        // ── Tarih (d) ─────────────────────────────────────────────────────────
        Object rawDate = row.get("d");
        if (rawDate == null) {
            log.debug("Satır {}: 'd' alanı null — atlandı", index);
            return null;
        }
        LocalDate date;
        try {
            date = parseDate(rawDate.toString().trim());
        } catch (DateTimeParseException e) {
            log.warn("Satır {}: tarih parse hatası '{}' — atlandı", index, rawDate);
            return null;
        }

        // ── Tutar (a) ─────────────────────────────────────────────────────────
        BigDecimal amount = AmountNormalizer.normalize(row.get("a"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Satır {}: geçersiz/sıfır tutar '{}' — atlandı", index, row.get("a"));
            return null;
        }

        // ── Açıklama (n) ──────────────────────────────────────────────────────
        String description = row.get("n") != null
                ? row.get("n").toString().trim() : "Bilinmeyen";

        // ── Taksit indeksi (t): 0=normal, N>0=currentInstallment ─────────────
        int t = 0;
        Object tRaw = row.get("t");
        if (tRaw instanceof Number num) {
            t = num.intValue();
        } else if (tRaw != null) {
            try { t = Integer.parseInt(tRaw.toString().trim()); }
            catch (NumberFormatException ignored) {}
        }
        boolean isInstallment = t > 0;
        Integer currentInstallment = isInstallment ? t : null;

        // ── Java tarafı kategori ve abonelik tespiti ──────────────────────────
        String category = detectGranularCategory(description);
        boolean isSubscription = detectSubscription(description);

        return new TransactionDto(date, description, amount, category, "TRY",
                isSubscription, isInstallment, currentInstallment, null, null);
    }

    /**
     * Açıklamaya göre granüler Türkçe kategori döner.
     * CategorizationService.mapLlmLabel() ile uyumlu etiketler üretir.
     */
    String detectGranularCategory(String description) {
        if (description == null || description.isBlank()) return "Diğer";
        // İ (U+0130) → i önce replace edilmeli; Locale.ROOT bunu "i̇" yapıyor
        String lower = description.replace('İ', 'i').toLowerCase(Locale.ROOT);

        if (lower.contains("migros") || lower.contains("bim") || lower.contains("a101")
                || lower.contains("şok") || lower.contains("carrefour") || lower.contains("hakmar")
                || lower.contains("macro") || lower.contains("kiler") || lower.contains("metro")
                || lower.contains("market") || lower.contains("manav") || lower.contains("kasap")
                || lower.contains("fırın") || lower.contains("ekmek") || lower.contains("bakkal")
                || lower.contains("kuruyemiş") || lower.contains("gıda") || lower.contains("pazar")
                || lower.contains("sebze") || lower.contains("meyve")) return "Market";

        if (lower.contains("starbucks") || lower.contains("gloria") || lower.contains("kahve")
                || lower.contains("espresso") || lower.contains("cafe") || lower.contains("kafe")
                || lower.contains("coffee")) return "Kafe";

        if (lower.contains("restoran") || lower.contains("restaurant") || lower.contains("burger")
                || lower.contains("pizza") || lower.contains("döner") || lower.contains("kebap")
                || lower.contains("balık") || lower.contains("mcdonald") || lower.contains("kfc")
                || lower.contains("popeyes") || lower.contains("subway") || lower.contains("noodle")
                || lower.contains("yemek")) return "Restoran";

        if (lower.contains("iett") || lower.contains("metrobüs") || lower.contains("dolmuş")
                || lower.contains("taksi") || lower.contains("uber") || lower.contains("bitaksi")
                || lower.contains("vapur") || lower.contains("marmaray") || lower.contains("tcdd")
                || lower.contains("otobüs") || lower.contains("ukome")
                || lower.contains("otoyol") || lower.contains("hgs") || lower.contains("ogs")) return "Ulaşım";

        if (lower.contains("shell") || lower.contains("opet") || lower.contains("total")
                || lower.contains("petrol") || lower.contains("benzin") || lower.contains("akaryakıt")
                || lower.contains("lpg") || (lower.contains("bp") && lower.length() <= 6)) return "Akaryakıt";

        if (lower.contains("iski") || lower.contains("igdas") || lower.contains("igdaş")
                || lower.contains("ayedas") || lower.contains("bedas") || lower.contains("elektrik")
                || lower.contains("doğalgaz") || lower.contains("turkcell") || lower.contains("vodafone")
                || lower.contains("türk telekom") || lower.contains("internet") || lower.contains("fatura")) return "Fatura";

        if (lower.contains("kira") || lower.contains("aidat")) return "Kira";

        if (lower.contains("eczane") || lower.contains("pharmacy") || lower.contains("hastane")
                || lower.contains("klinik") || lower.contains("doktor") || lower.contains("diş")
                || lower.contains("optik") || lower.contains("laborat")) return "Sağlık";

        if (lower.contains("netflix") || lower.contains("spotify") || lower.contains("youtube")
                || lower.contains("disney") || lower.contains("sinema") || lower.contains("tiyatro")
                || lower.contains("konser") || lower.contains("amazon prime") || lower.contains("todtv")) return "Eğlence";

        if (lower.contains("apple") || lower.contains("samsung") || lower.contains("mediamarkt")
                || lower.contains("vatan") || lower.contains("teknosa") || lower.contains("arçelik")
                || lower.contains("beko") || lower.contains("amazon") || lower.contains("bilgisayar")
                || lower.contains("trendyol")) return "Teknoloji";

        if (lower.contains("zara") || lower.contains("lcwaikiki") || lower.contains("lc waikiki")
                || lower.contains("h&m") || lower.contains("mavi") || lower.contains("koton")
                || lower.contains("defacto") || lower.contains("adidas") || lower.contains("nike")
                || lower.contains("giyim") || lower.contains("ayakkabı") || lower.contains("tekstil")) return "Giyim";

        if (lower.contains("üniversite") || lower.contains("kurs") || lower.contains("kitap")
                || lower.contains("udemy") || lower.contains("coursera") || lower.contains("okul")
                || lower.contains("dershane")) return "Eğitim";

        if (lower.contains("sigorta") || lower.contains("kasko") || lower.contains("emeklilik")
                || lower.contains("dask")) return "Sigorta";

        return "Diğer";
    }

    /** Açıklama bilinen dijital abonelik servislerinden birine aitse {@code true} döner. */
    boolean detectSubscription(String description) {
        if (description == null || description.isBlank()) return false;
        String lower = description.replace('İ', 'i').toLowerCase(Locale.ROOT);
        return lower.contains("netflix") || lower.contains("spotify") || lower.contains("youtube")
                || lower.contains("appletv") || lower.contains("apple tv") || lower.contains("disney")
                || lower.contains("amazon prime") || lower.contains("amazonprime") || lower.contains("icloud")
                || lower.contains("google one") || lower.contains("googleone")
                || lower.contains("adobe") || lower.contains("office365") || lower.contains("office 365")
                || lower.contains("todtv") || lower.contains("üyelik");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Banka Tipi Tespiti
    // ─────────────────────────────────────────────────────────────────────────

    private enum BankType { HALKBANK, ISBANK, YAPIKREDI, UNKNOWN }

    private BankType detectBankType(String text) {
        if (text.contains("HALKBANK") || text.contains("paraf.com.tr")
                || text.contains("Halk Bankası"))                              return BankType.HALKBANK;
        if (text.contains("isbank.com.tr") || text.contains("MaxiPuan")
                || text.contains("MAXIPUAN"))                                  return BankType.ISBANK;
        if (text.contains("YAPI ve KREDİ BANKASI") || text.contains("worldcard.com.tr")
                || text.contains("WORLDPUAN"))                                 return BankType.YAPIKREDI;
        return BankType.UNKNOWN;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction Router
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bir satırın LOCAL olarak (LLM'siz) parse edilip edilemeyeceğini belirler.
     * Yapı Kredi (UNKNOWN): dd.MM.yyyy formatı + merchant cache hit gerekir.
     * İş Bankası: dd/MM/yyyy + TR suffix + cache hit gerekir.
     * Halkbank: dd/MM/yyyy + İngiliz tutar + ParafPara + cache hit gerekir.
     */
    private boolean isHighConfidence(String line, String nextLine, BankType bankType) {
        return switch (bankType) {
            case ISBANK    -> isIsbankHighConfidence(line, nextLine);
            case HALKBANK  -> isHalkbankHighConfidence(line, nextLine);
            case YAPIKREDI -> isYapiKrediHighConfidence(line, nextLine);
            default        -> isDefaultHighConfidence(line, nextLine);
        };
    }

    private boolean isDefaultHighConfidence(String line, String nextLine) {
        Matcher m = HIGH_CONF_LINE.matcher(line);
        if (!m.matches()) return false;
        if (TAKSIT_COUNT_IN_LINE.matcher(line).find()) return false;
        if (TAKSIT_SUBLINE_MARKER.matcher(nextLine).find()) return false;
        String description = m.group(2).trim();
        if (description.length() > 50) return false;
        if (POS_CODE.matcher(description).find()) return false;
        if (TURKISH_AMOUNT_IN_LINE.matcher(description).find()) return false;
        try {
            LocalDate date = parseDate(m.group(1));
            if (date.getYear() < 2020 || date.getYear() > 2030) return false;
        } catch (Exception e) { return false; }
        return merchantCacheService.isKnown(description);
    }

    private boolean isIsbankHighConfidence(String line, String nextLine) {
        Matcher m = HIGH_CONF_ISBANK.matcher(line);
        if (!m.matches()) return false;
        if (TAKSIT_COUNT_IN_LINE.matcher(line).find()) return false;
        if (ISBANK_TAKSIT_IN_LINE.matcher(line).find()) return false;
        if (TAKSIT_SUBLINE_MARKER.matcher(nextLine).find()) return false;
        String rawDesc = m.group(2).trim();
        String description = stripIsbankCitySuffix(rawDesc);
        if (description.length() > 60) return false;
        if (POS_CODE.matcher(description).find()) return false;
        try {
            LocalDate date = parseDate(m.group(1));
            if (date.getYear() < 2020 || date.getYear() > 2030) return false;
        } catch (Exception e) { return false; }
        return merchantCacheService.isKnown(description);
    }

    private boolean isHalkbankHighConfidence(String line, String nextLine) {
        Matcher m = HIGH_CONF_HALKBANK.matcher(line);
        if (!m.matches()) return false;
        if (TAKSIT_COUNT_IN_LINE.matcher(line).find()) return false;
        if (TAKSIT_SUBLINE_MARKER.matcher(nextLine).find()) return false;
        String rawDesc = m.group(2).trim();
        String description = stripHalkbankCity(rawDesc);
        if (description.length() > 60) return false;
        if (POS_CODE.matcher(description).find()) return false;
        try {
            LocalDate date = parseDate(m.group(1));
            if (date.getYear() < 2020 || date.getYear() > 2030) return false;
        } catch (Exception e) { return false; }
        return merchantCacheService.isKnown(description);
    }

    /**
     * HIGH confidence satırını LLM'siz parse eder.
     * Parse başarısız olursa {@link Optional#empty()} döner ve satır LLM'e yönlendirilir.
     */
    private Optional<TransactionDto> parseLineLocally(String line, BankType bankType) {
        return switch (bankType) {
            case ISBANK    -> parseIsbankLineLocally(line);
            case HALKBANK  -> parseHalkbankLineLocally(line);
            case YAPIKREDI -> parseYapiKrediLineLocally(line);
            default        -> parseDefaultLineLocally(line);
        };
    }

    private Optional<TransactionDto> parseDefaultLineLocally(String line) {
        Matcher m = HIGH_CONF_LINE.matcher(line);
        if (!m.matches()) return Optional.empty();
        try {
            LocalDate  date        = parseDate(m.group(1));
            BigDecimal amount      = AmountNormalizer.normalize(m.group(3));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
            String  description    = cleanLocalDescription(m.group(2).trim());
            String  category       = detectGranularCategory(description);
            boolean isSubscription = detectSubscription(description);
            return Optional.of(new TransactionDto(
                    date, description, amount, category, "TRY",
                    isSubscription, false, null, null, null));
        } catch (Exception e) {
            log.debug("[local-parser] Parse başarısız: '{}' — {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TransactionDto> parseIsbankLineLocally(String line) {
        Matcher m = HIGH_CONF_ISBANK.matcher(line);
        if (!m.matches()) return Optional.empty();
        try {
            LocalDate  date        = parseDate(m.group(1));
            BigDecimal amount      = AmountNormalizer.normalize(m.group(3));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
            String rawDesc     = m.group(2).trim();
            String description = cleanLocalDescription(stripIsbankCitySuffix(rawDesc));
            String  category       = detectGranularCategory(description);
            boolean isSubscription = detectSubscription(description);
            return Optional.of(new TransactionDto(
                    date, description, amount, category, "TRY",
                    isSubscription, false, null, null, null));
        } catch (Exception e) {
            log.debug("[local-parser][isbank] Parse başarısız: '{}' — {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TransactionDto> parseHalkbankLineLocally(String line) {
        Matcher m = HIGH_CONF_HALKBANK.matcher(line);
        if (!m.matches()) return Optional.empty();
        try {
            LocalDate  date        = parseDate(m.group(1));
            BigDecimal amount      = AmountNormalizer.normalize(m.group(3));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
            String rawDesc     = m.group(2).trim();
            String description = cleanLocalDescription(stripHalkbankCity(rawDesc));
            String  category       = detectGranularCategory(description);
            boolean isSubscription = detectSubscription(description);
            return Optional.of(new TransactionDto(
                    date, description, amount, category, "TRY",
                    isSubscription, false, null, null, null));
        } catch (Exception e) {
            log.debug("[local-parser][halkbank] Parse başarısız: '{}' — {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isYapiKrediHighConfidence(String line, String nextLine) {
        Matcher m = HIGH_CONF_YAPIKREDI.matcher(line);
        if (!m.matches()) return false;
        if (TAKSIT_COUNT_IN_LINE.matcher(line).find()) return false;   // kalan tutar/N var → LLM
        if (TAKSIT_SUBLINE_MARKER.matcher(nextLine).find()) return false;
        String rawDesc = m.group(2).trim();
        String lowerDesc = rawDesc.replace('İ', 'i').toLowerCase(Locale.ROOT);
        if (lowerDesc.contains("ödeme-internet") || lowerDesc.contains("taksitlendirme")) return false;
        String description = stripYapiKrediCitySuffix(rawDesc);
        if (description.length() > 60) return false;
        if (POS_CODE.matcher(description).find()) return false;
        LocalDate date = extractTurkishDate(line);
        if (date == null || date.getYear() < 2020 || date.getYear() > 2030) return false;
        return merchantCacheService.isKnown(description);
    }

    private Optional<TransactionDto> parseYapiKrediLineLocally(String line) {
        Matcher m = HIGH_CONF_YAPIKREDI.matcher(line);
        if (!m.matches()) return Optional.empty();
        try {
            LocalDate  date        = extractTurkishDate(m.group(1));
            if (date == null) return Optional.empty();
            BigDecimal amount      = AmountNormalizer.normalize(m.group(3));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
            String rawDesc     = m.group(2).trim();
            String description = cleanLocalDescription(stripYapiKrediCitySuffix(rawDesc));
            String  category       = detectGranularCategory(description);
            boolean isSubscription = detectSubscription(description);
            return Optional.of(new TransactionDto(
                    date, description, amount, category, "TRY",
                    isSubscription, false, null, null, null));
        } catch (Exception e) {
            log.debug("[local-parser][yapikredi] Parse başarısız: '{}' — {}", line, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Satırın LLM chunk'ına dahil edilmeye değer bir işlem adayı olup olmadığını kontrol eder.
     * Banka tipine göre farklı tutar formatları ve skip kuralları uygulanır.
     */
    private boolean isTransactionCandidate(String line, BankType bankType) {
        if (line.length() < 8) return false;
        if (TAKSIT_SUBLINE_MARKER.matcher(line).find()) return false;

        // Halkbank: DD/MM/YYYY ile başlayan her satır işlem başlangıcıdır —
        // tutar bazen sonraki satırda geldiğinden burada amount kontrolü atlanır
        if (bankType == BankType.HALKBANK && SLASH_DATE_LINE.matcher(line).find()) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("hesaptan ödeme"))     return false;
            if (lower.contains("sonradantak.alacak")) return false;
            return true;
        }

        boolean hasTurkishAmount = TURKISH_AMOUNT_IN_LINE.matcher(line).find();
        boolean hasEnglishAmount = ENGLISH_AMOUNT_IN_LINE.matcher(line).find();
        if (!hasTurkishAmount && !hasEnglishAmount) return false;

        // Banka bazlı ek skip kuralları
        if (bankType == BankType.ISBANK) {
            // İş Bankası: servis ücreti satırlarını, hesap aktarımlarını ve negatif tutarları atla
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("fatura ödeme ücreti")) return false;
            if (lower.contains("hesaptan aktarim"))    return false;
            if (lower.contains("maxipuan ilave"))      return false;
            if (line.contains("-") && TURKISH_AMOUNT_IN_LINE.matcher(line).find()
                    && line.matches(".*-\\d{1,3}(?:\\.\\d{3})*,\\d{2}.*")) return false;
        }
        if (bankType == BankType.HALKBANK) {
            // Halkbank: ödeme satırları ve SonradanTak alacak satırlarını atla
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("hesaptan ödeme"))      return false;
            if (lower.contains("sonradantak.alacak"))  return false;
        }
        if (bankType == BankType.YAPIKREDI) {
            // Yapı Kredi: internet ödeme ve taksitlendirme faiz satırlarını atla
            // (PdfTextCleaner bu satırları genellikle zaten filtreler — bu güvenlik katmanı)
            String lower = line.replace('İ', 'i').toLowerCase(Locale.ROOT);
            if (lower.contains("ödeme-internet"))      return false;
            if (lower.contains("taksitlendirme"))      return false;
            // Tutarı + ile başlayan satırlar ödeme/iade — atla
            if (line.matches(".*\\s\\+\\d{1,3}(?:\\.\\d{3})*,\\d{2}.*")) return false;
        }

        return HIGH_CONF_LINE.matcher(line).matches()
                || HIGH_CONF_ISBANK.matcher(line).matches()
                || HIGH_CONF_HALKBANK.matcher(line).matches()
                || TURKISH_DATE_IN_LINE.matcher(line).find()
                || SLASH_DATE_LINE.matcher(line).find()
                || line.matches("^\\d{4}[-/]\\d{2}[-/]\\d{2}.*")
                || line.matches("^\\d{1,2}[./]\\d{1,2}[./]\\d{4}.*");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Şehir/ülke suffix temizleyiciler
    // ─────────────────────────────────────────────────────────────────────────

    /** İş Bankası açıklamasından sondaki " ŞEHİR TR" veya " ŞEHİR TU" suffix'ini çıkarır. */
    private String stripIsbankCitySuffix(String description) {
        return description.replaceAll("\\s+\\S+\\s+T[RU]\\s*$", "").trim();
    }

    /** Halkbank açıklamasından sondaki şehir kelimesini çıkarır. */
    private String stripHalkbankCity(String description) {
        return description.replaceAll("\\s+\\S+\\s*$", "").trim();
    }

    /** Yapı Kredi açıklamasından sondaki " ŞEHİR TR" veya " ŞEHİR TU" suffix'ini çıkarır. */
    private String stripYapiKrediCitySuffix(String description) {
        return description.replaceAll("\\s+\\S+\\s+T[RU]\\s*$", "").trim();
    }

    /**
     * Yerel parse edilen açıklamadan ülke/şebeke kodlarını ve sondaki puan değerlerini temizler.
     * (Tutar ve tarih regex tarafından ayrıldığından burada yalnızca artefaktlar giderilir.)
     */
    private String cleanLocalDescription(String raw) {
        String s = COUNTRY_CODE.matcher(raw).replaceAll(" ").trim();
        s = TRAILING_INT.matcher(s).replaceAll("").trim();
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s.isEmpty() ? "Bilinmeyen" : s;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı metodlar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Birden fazla tarih formatını dener; ilki başarılı olursa döner.
     * Desteklenen formatlar: {@link #DATE_FORMATTERS} listesine bakın.
     */
    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new DateTimeParseException(
                "Hiçbir format eşleşmedi: " + raw, raw, 0);
    }

    /**
     * LLM bazen yanıtı {@code ```json ... ```} bloğu içinde döner.
     * Bu metot markdown çitleri temizler ve ham JSON'ı döner.
     */
    private String stripMarkdownFences(String raw) {
        return raw.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*",    "")
                .replaceAll("```$",            "")
                .trim();
    }

    /**
     * Temiz metni satır bazlı {@code linesPerChunk}-satırlık chunk'lara böler.
     * Chunk sınırlarında taksit alt satırlarının kopmaması için son {@link #CHUNK_OVERLAP_LINES}
     * satır bir sonraki chunk'ın başına eklenir (örtüşme).
     */
    private List<String> splitIntoChunks(String text, int linesPerChunk) {
        if (text == null || text.isBlank()) return List.of(text == null ? "" : text);
        String[]     lines  = text.split("\n", -1);
        List<String> chunks = new ArrayList<>();
        int stride = Math.max(1, linesPerChunk - CHUNK_OVERLAP_LINES);
        for (int start = 0; start < lines.length; start += stride) {
            int end = Math.min(start + linesPerChunk, lines.length);
            chunks.add(String.join("\n", Arrays.copyOfRange(lines, start, end)));
            if (end == lines.length) break;
        }
        return chunks;
    }

    /**
     * Tek bir chunk'ın ham LLM JSON'ını parse eder.
     * Hata durumunda chunk atlanır; exception tüm pipeline'ı patlatmaz.
     */
    private List<TransactionDto> parseChunk(String rawJson, int chunkIdx) {
        try {
            String stripped  = stripMarkdownFences(rawJson);
            String sanitized = sanitizeJson(stripped);
            String repaired  = repairJson(sanitized);
            log.debug("[chunk-{}] JSON hazır (ilk 200 karakter): {}",
                    chunkIdx, repaired.substring(0, Math.min(200, repaired.length())));
            return parseRowsFaultTolerant(repaired);
        } catch (Exception e) {
            log.warn("[chunk-{}] Parse başarısız, chunk atlanıyor: {}", chunkIdx, e.getMessage());
            return List.of();
        }
    }

    /** Deduplication anahtarı: tarih + açıklama + tutar (2 ondalık, yuvarlama HALF_UP). */
    private String dtoKey(TransactionDto dto) {
        return dto.date() + "|" + dto.description() + "|" +
               dto.amount().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Metindeki satır sayısını döner; boş/null metin için 0. */
    private int countLines(String text) {
        return (text == null || text.isEmpty()) ? 0 : text.split("\n", -1).length;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Onarıcı — truncated cevapları kurtarır
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * max_tokens sınırına takılan (yarıda kesilen) LLM yanıtını onarmaya çalışır.
     *
     * <p>Strateji: JSON dizisinin son tam {@code }} karakterine kadar olan kısmı alır,
     * sondaki fazladan virgülü temizler ve {@code ]} ekleyerek kapatır.
     * Böylece yarım kalan son obje atılır ama geri kalan tüm işlemler kurtarılır.
     *
     * <p>Örnekler:
     * <pre>
     *  Gelen:   [{...},{...},{"date":"2026
     *  Onarıldı: [{...},{...}]        (son yarım obje atıldı)
     *
     *  Gelen:   [{...},{...},
     *  Onarıldı: [{...},{...}]        (sondaki virgül temizlendi)
     *
     *  Gelen:   [{...},{...}]
     *  Onarıldı: [{...},{...}]        (zaten geçerli — dokunulmadı)
     * </pre>
     *
     * @param json markdown çitleri ve sayı sanitasyonu uygulanmış JSON string'i
     * @return geçerli (veya daha geçerli) JSON string'i
     */
    // ─────────────────────────────────────────────────────────────────────────
    // Kural Tabanlı Taksit Post-Processor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Temiz PDF metnini satır satır tarayarak "N / M taksidi" pattern'larını bulur,
     * hemen üstündeki işlem satırından tarih ve tutar bilgisini çıkarır,
     * eşleşen DTO'ları güncelleyerek taksit bilgilerini ekler.
     *
     * <p>LLM'in gözden kaçırdığı veya yanlış okuduğu taksitleri kural tabanlı kurtarır.
     * Yapı Kredi / Worldcard formatını doğrudan destekler.
     *
     * @param dtos      LLM'den parse edilen DTO listesi
     * @param cleanText PdfTextCleaner'dan geçmiş temiz PDF metni
     * @return Taksit bilgileri zenginleştirilmiş DTO listesi
     */
    private List<TransactionDto> enrichWithInstallments(List<TransactionDto> dtos, String cleanText) {
        String[] lines = cleanText.split("\n");
        List<TransactionDto> result = new ArrayList<>(dtos);
        int enriched = 0;
        int created  = 0;

        for (int i = 1; i < lines.length; i++) {
            Matcher tm = TAKSIT_SUBLINE_FULL.matcher(lines[i]);
            if (!tm.find()) continue;

            int current = Integer.parseInt(tm.group(2));
            int total   = Integer.parseInt(tm.group(3));

            // Bir üst satırı dene; tarih bulunamazsa iki üst satıra bak
            // (TAKSİTLENDİRME FAİZİ satırı araya girebilir)
            String prevLine = lines[i - 1].trim();
            LocalDate date  = extractTurkishDate(prevLine);
            if (date == null && i >= 2) {
                prevLine = lines[i - 2].trim();
                date     = extractTurkishDate(prevLine);
            }
            if (date == null) continue;

            List<BigDecimal> amounts = extractTurkishAmounts(prevLine);
            if (amounts.isEmpty()) continue;
            BigDecimal txAmount = amounts.get(0); // ilk tutar = taksit ödemesi

            // Eşleşen DTO'yu bul: aynı tarih + tutar
            boolean found = false;
            for (int j = 0; j < result.size(); j++) {
                TransactionDto dto = result.get(j);
                if (!dto.date().equals(date)) continue;
                if (dto.amount().subtract(txAmount).abs().compareTo(new BigDecimal("0.05")) >= 0) continue;

                // Taksit bilgisi yanlış veya eksik — her durumda üzerine yaz
                result.set(j, new TransactionDto(
                    dto.date(), dto.description(), dto.amount(),
                    dto.category(), dto.currency(), dto.isSubscription(),
                    true, current, total, dto.categoryEnum()
                ));
                log.info("[taksit-fix] güncellendi {}/{} → {} | {} | {}TL",
                    current, total, dto.date(), dto.description(), dto.amount());
                enriched++;
                found = true;
                break;
            }

            // LLM bu işlemi tamamen kaçırdıysa sıfırdan DTO oluştur
            if (!found) {
                String description = extractDescriptionFromLine(prevLine);
                result.add(new TransactionDto(
                    date, description, txAmount, "Diğer", "TRY",
                    false, true, current, total, null
                ));
                log.info("[taksit-fix] yeni DTO → {}/{} | {} | {} | {}TL",
                    current, total, date, description, txAmount);
                created++;
            }
        }

        if (enriched > 0 || created > 0) {
            log.info("[taksit-fix] tamamlandı — {} güncellendi, {} yeni DTO oluşturuldu.", enriched, created);
        } else {
            log.info("[taksit-fix] Kural tabanlı tarayıcı yeni taksit bulamadı " +
                     "(LLM zaten tespit etmiş veya metinde 'taksidi' satırı yok).");
        }
        return result;
    }

    /**
     * Satırdaki ilk Türkçe tarif formatını ("14 Ocak 2026") parse eder.
     * Bulunamazsa {@code null} döner.
     */
    private LocalDate extractTurkishDate(String line) {
        Matcher m = TURKISH_DATE_IN_LINE.matcher(line);
        if (!m.find()) return null;
        int day  = Integer.parseInt(m.group(1));
        Integer month = TURKISH_MONTHS.get(m.group(2).toLowerCase(Locale.forLanguageTag("tr")));
        if (month == null) return null;
        int year = Integer.parseInt(m.group(3));
        try { return LocalDate.of(year, month, day); } catch (Exception e) { return null; }
    }

    /**
     * Taksit işlem satırından açıklama metni çıkarır.
     * LLM'in tamamen atladığı işlemler için fallback açıklama üretir.
     * Tarih, tutarlar, kalan taksit suffix'i, ülke kodu ve sonu puan değerlerini silerek
     * mağaza/işyeri adını döner.
     */
    private String extractDescriptionFromLine(String line) {
        String s = TURKISH_DATE_IN_LINE.matcher(line).replaceFirst("").trim();
        s = TURKISH_AMOUNT_IN_LINE.matcher(s).replaceAll(" ").trim();
        s = REMAINING_COUNT.matcher(s).replaceAll(" ").trim();
        s = COUNTRY_CODE.matcher(s).replaceAll(" ").trim();
        s = TRAILING_INT.matcher(s).replaceAll("").trim();
        s = s.replaceAll("\\s{2,}", " ").trim();
        return s.isEmpty() ? "Bilinmeyen" : s;
    }

    /**
     * Satırdaki tüm Türk formatı tutarları ("412,53", "1.765,83") döner.
     * Binlik noktalı formatı otomatik normalize eder.
     */
    private List<BigDecimal> extractTurkishAmounts(String line) {
        Matcher m = TURKISH_AMOUNT_IN_LINE.matcher(line);
        List<BigDecimal> amounts = new ArrayList<>();
        while (m.find()) {
            BigDecimal val = AmountNormalizer.normalize(m.group(1));
            if (val != null && val.compareTo(BigDecimal.ZERO) > 0) {
                amounts.add(val);
            }
        }
        return amounts;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Onarıcı — truncated cevapları kurtarır
    // ─────────────────────────────────────────────────────────────────────────

    String repairJson(String json) {
        String s = json.trim();

        // Zaten geçerli bir dizi kapanışıyla bitiyor → dokunma
        if (s.endsWith("]")) return s;

        log.warn("[repair] JSON ] ile bitmiyor — onarım deneniyor. Son 80 karakter: ...{}",
                s.substring(Math.max(0, s.length() - 80)));

        // Son tam '}' konumunu bul
        int lastBrace = s.lastIndexOf('}');
        if (lastBrace < 0) {
            // Hiç tam obje yok — boş dizi dön
            log.warn("[repair] Hiç tam JSON objesi bulunamadı — [] döndürülüyor.");
            return "[]";
        }

        // Son '}' sonrasını kes
        String truncated = s.substring(0, lastBrace + 1).stripTrailing();

        // Sondaki virgülü temizle: [{...},{...},  →  [{...},{...}
        if (truncated.endsWith(",")) {
            truncated = truncated.substring(0, truncated.length() - 1).stripTrailing();
        }

        // Açılış '[' yoksa ekle
        if (!truncated.startsWith("[")) {
            truncated = "[" + truncated;
        }

        String repaired = truncated + "]";
        log.warn("[repair] Onarım tamamlandı. Orijinal: {} karakter → Onarıldı: {} karakter.",
                json.length(), repaired.length());
        return repaired;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Sayı Sanitizer — Türk banka formatı düzeltici
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LLM'den gelen JSON string'indeki malformed {@code "amount"} değerlerini
     * Jackson'a vermeden önce düzeltir.
     *
     * <p>Düzeltilen durumlar:
     * <ul>
     *   <li>{@code "amount": 1.250.00}  → {@code "amount": 1250.00}  (iki nokta)</li>
     *   <li>{@code "amount": 1.250,00}  → {@code "amount": 1250.00}  (Türk formatı)</li>
     *   <li>{@code "amount": "1.250,00"} → {@code "amount": 1250.00} (string → number)</li>
     *   <li>{@code "amount": 12.480,37} → {@code "amount": 12480.37} (binlik nokta)</li>
     * </ul>
     *
     * @param json LLM'den gelen, markdown çitleri temizlenmiş JSON string'i
     * @return amount değerleri düzeltilmiş JSON string'i
     */
    String sanitizeJson(String json) {
        StringBuffer sb = new StringBuffer();
        Matcher m = AMOUNT_JSON_PATTERN.matcher(json);
        int fixCount = 0;

        while (m.find()) {
            String keyPart = m.group(1);  // "amount":
            String rawNum  = m.group(3);  // ham sayı
            String fixed   = fixAmountString(rawNum);

            if (!fixed.equals(rawNum)) {
                log.debug("[sanitize] amount düzeltildi: '{}' → '{}'", rawNum, fixed);
                fixCount++;
            }
            // Tırnak karakterlerini (grup 2,4) bilerek atıyoruz: sonuç her zaman JSON number
            m.appendReplacement(sb, Matcher.quoteReplacement(keyPart + fixed));
        }
        m.appendTail(sb);

        if (fixCount > 0) {
            log.info("[sanitize] {} adet amount değeri düzeltildi.", fixCount);
        }
        return sb.toString();
    }

    /**
     * Ham sayı string'ini geçerli JSON number formatına (ondalık nokta) çevirir.
     *
     * <p>Kural seti:
     * <ol>
     *   <li>Zaten geçerliyse ({@code \d+(\.\d{1,2})?}) değiştirme</li>
     *   <li>Virgül içeriyorsa → Türk formatı: nokta=binlik, virgül=ondalık</li>
     *   <li>Birden fazla nokta → son nokta ondalık, diğerleri binlik</li>
     *   <li>Tek nokta, sonrasında tam 3 rakam → binlik nokta (1.250 → 1250)</li>
     * </ol>
     */
    String fixAmountString(String raw) {
        raw = raw.trim();
        if (raw.isEmpty()) return raw;

        // ── 1. Zaten geçerli JSON number ─────────────────────────────────────
        if (raw.matches("\\d+(\\.\\d{1,2})?")) return raw;

        // ── 2. Virgül var → Türk/Avrupa formatı (nokta=binlik, virgül=ondalık)
        //      Örnekler: 1.250,00 → 1250.00 | 12.480,37 → 12480.37 | 89,90 → 89.90
        if (raw.contains(",")) {
            return raw.replace(".", "").replace(",", ".");
        }

        // ── 3. Birden fazla nokta → son nokta ondalık, öncekiler binlik
        //      Örnek: 1.250.00 → 1250.00 | 12.480.37 → 12480.37
        long dotCount = raw.chars().filter(c -> c == '.').count();
        if (dotCount > 1) {
            int lastDot  = raw.lastIndexOf('.');
            String intPart = raw.substring(0, lastDot).replace(".", "");
            String decPart = raw.substring(lastDot + 1);
            return intPart + "." + decPart;
        }

        // ── 4. Tek nokta: 3 haneli son kısım → binlik ayraç (1.250 → 1250)
        if (raw.contains(".")) {
            int dot = raw.indexOf('.');
            String afterDot = raw.substring(dot + 1);
            if (afterDot.length() == 3 && afterDot.matches("\\d+")) {
                return raw.replace(".", "");
            }
        }

        return raw;
    }
}
