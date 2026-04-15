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

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
     * Türk bankası ekstrelerine özel LLM prompt'u — kasıtlı olarak kısa tutulmuştur.
     *
     * <p>Uzun prompt → model JSON yerine açıklamaya odaklanır ve cevap token'ları israf edilir.
     * Bu template sadece kuralları ve örnekleri içerir; model en kısa yoldan JSON üretir.
     */
    private static final String PROMPT_TEMPLATE = """
            Turkish bank statement below. Extract spending transactions and return ONLY a JSON array.
            No intro sentence, no explanation, no markdown fences — start with [ and end with ].

            FORMAT (each object exactly like this):
            {"date":"YYYY-MM-DD","description":"Name","amount":1234.56,"category":"Category","currency":"TRY","isSubscription":false,"isInstallment":false,"currentInstallment":null,"totalInstallments":null}

            AMOUNT RULES — CRITICAL:
            • Use ONLY dot as decimal separator. NEVER output a thousands-separator dot.
            • 1.250,00 TL → 1250.00  |  12.480,37 TL → 12480.37  |  89,90 TL → 89.90
            • Wrong: 1.250.00  Wrong: 1.250,00  Wrong: "1250.00"   Right: 1250.00

            DATE: ISO 8601 output. 15.03.2026 or 15/03/2026 → "2026-03-15"

            DESCRIPTION: Store name only. Remove POS ID, terminal, ref no, branch, city. Fix casing.
            Keep Turkish chars (ğ ş ı ç ö ü). MKT→Market, REST→Restoran, PETROL→Akaryakıt.

            CATEGORY RULES — assign exactly one, strictly follow this priority order:
            1. Market    → Migros, BİM, A101, ŞOK, Carrefour, Hakmar, Macro, Kiler, METRO,
                           Gıda, Manav, Kasap, Bakkal, Süpermarket, Market, Kuruyemiş, Fırın,
                           Ekmek, Pazar, Sebze, Meyve, Aktarı, Temizlik (grocery stores only)
            2. Kafe      → Starbucks, Gloria Jeans, Kahve Dünyası, Espresso, Cafe, Kafe, Coffee
            3. Restoran  → Restaurant, Restoran, Burger, Pizza, Döner, Kebap, Balık, Yemek,
                           Fast food, McDonald's, KFC, Popeyes, Subway, Noodle
            4. Ulaşım    → İETT, Metro, Metrobüs, Otobüs, Dolmuş, Taksi, Uber, BiTaksi,
                           Trambüs, Vapur, Marmaray, Tren, TCDD, UKOME, Toplu Taşıma
            5. Akaryakıt → Shell, BP, Opet, Total, Petrol, Benzin, Akaryakıt, LPG
            6. Fatura    → ISKI, IGDAS, AYEDAS, BEDAS, Elektrik, Doğalgaz, Su faturası,
                           Turkcell, Vodafone, Türk Telekom, İnternet, Fatura
            7. Kira      → Kira, Aidat, Site ücreti
            8. Sağlık    → Eczane, Pharmacy, Hastane, Klinik, Doktor, Diş, Optik, Laborat
            9. Eğlence   → Netflix, Spotify, YouTube, Disney, Sinema, Tiyatro, Konser, Oyun
            10. Teknoloji → Apple, Samsung, Mediamarkt, Vatan, Teknosa, Arçelik, Beko,
                            Amazon, Trendyol Elektronik, Bilgisayar, Telefon, Tablet
            11. Giyim    → Zara, H&M, LC Waikiki, Mavi, Koton, Pull&Bear, DeFacto, Adidas,
                           Nike, Giyim, Ayakkabı, Çanta, Tekstil
            12. Eğitim   → Üniversite, Kurs, Kitap, Udemy, Coursera, Okul, Dershane
            13. Sigorta  → Sigorta, Kasko, Emeklilik, BES, DASK
            14. Diğer    → Only if none of the above match.
            IMPORTANT: "Diğer" is a last resort. Always try to match one of the 13 named categories first.

            isSubscription true: Netflix/Spotify/YouTube/AppleTV/Disney+/Amazon Prime/iCloud/
                                  GoogleOne/Gym üyelik/Adobe/Office365/Dergi abonelik
            isSubscription false: all other transactions

            INSTALLMENT RULES — CRITICAL, NO EXCEPTIONS:
            • A transaction is installment (isInstallment:true) if its description contains ANY of:
              – A fraction pattern like 2/6, 3/12, 1/9, 4/24 (current/total)
              – The word "Taksit", "TAKSİT", "TAKSIT", "taksit", "taksitli", "TAKSİTLİ"
            • If isInstallment is true, you MUST set currentInstallment and totalInstallments.
              NEVER leave them null when isInstallment is true.
              – If fraction X/Y exists → currentInstallment:X, totalInstallments:Y
              – If only "Taksit" keyword (no fraction) → currentInstallment:1, totalInstallments:1

            SKIP: balance rows, IBAN, page headers/footers, VAT/fee lines, incoming transfers, refunds.
            Extract AT MOST 60 transactions.

            EXAMPLES:
            "15.03.2026 MIGROS 1.250,00 TL"
              → {"date":"2026-03-15","description":"Migros","amount":1250.00,"category":"Market","currency":"TRY","isSubscription":false,"isInstallment":false,"currentInstallment":null,"totalInstallments":null}
            "01.04.2026 GIDA MARKET 340,00 TL"
              → {"date":"2026-04-01","description":"Gıda Market","amount":340.00,"category":"Market","currency":"TRY","isSubscription":false,"isInstallment":false,"currentInstallment":null,"totalInstallments":null}
            "01.04.2026 NETFLIX.COM 149,90 TL"
              → {"date":"2026-04-01","description":"Netflix","amount":149.90,"category":"Eğlence","currency":"TRY","isSubscription":true,"isInstallment":false,"currentInstallment":null,"totalInstallments":null}
            "10.04.2026 SAMSUNG TV 3/12 TAKSİT 850,50 TL"
              → {"date":"2026-04-10","description":"Samsung TV","amount":850.50,"category":"Teknoloji","currency":"TRY","isSubscription":false,"isInstallment":true,"currentInstallment":3,"totalInstallments":12}
            "08.04.2026 APPLE STORE 2/6 TAKSIT 245,90 TL"
              → {"date":"2026-04-08","description":"Apple Store","amount":245.90,"category":"Teknoloji","currency":"TRY","isSubscription":false,"isInstallment":true,"currentInstallment":2,"totalInstallments":6}
            "12.04.2026 TAKSİT ÖDEMESİ 500,00 TL"
              → {"date":"2026-04-12","description":"Taksit Ödemesi","amount":500.00,"category":"Diğer","currency":"TRY","isSubscription":false,"isInstallment":true,"currentInstallment":1,"totalInstallments":1}

            Statement:
            %s
            """;

    /**
     * PDF metninden LLM'e gönderilecek maksimum karakter sayısı.
     *
     * <p>~3 000 token'a karşılık gelir; geri kalan token bütçesi (4096 max_tokens) JSON çıktısına ayrılır.
     * Daha uzun metinler bu sınırda kesilir ve log'a uyarı yazılır.
     */
    private static final int MAX_INPUT_CHARS = 12_000;

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
            "(\"amount\"\\s*:\\s*)(\"?)(\\d[\\d.]*(?:,\\d+)?)(\"?)",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final ObjectMapper objectMapper;

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
    // LLM JSON üretimi
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PDF dosyasını okur ve LLM ile işlem JSON'ı üretir.
     *
     * <p>Pre-processing adımı burada uygulanır:
     * <ol>
     *   <li>PDF → ham metin ({@link PdfService})</li>
     *   <li>Ham metin → temiz metin ({@link PdfTextCleaner})</li>
     *   <li>Temiz metin → prompt → LLM → JSON</li>
     * </ol>
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        log.info(">>> Extraction mode: LLM | apiKey: {}...",
                openAiApiKey.substring(0, Math.min(10, openAiApiKey.length())));

        // Adım A — PDF'ten ham metin
        String rawText = pdfService.extractText(file);
        log.info("[1/4] PDF okundu. Ham metin: {} karakter", rawText.length());

        if (rawText.isBlank()) {
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. PDF metin içermiyor; " +
                    "taranmış (image-only) veya şifreli bir PDF olabilir.");
        }

        // Ham metnin ilk 800 karakterini logla (debug için)
        log.debug("[1/4] Ham metin (ilk 800 karakter):\n{}",
                rawText.substring(0, Math.min(800, rawText.length())));

        // Adım B — Pre-processing: gürültü temizleme
        String cleanText = PdfTextCleaner.clean(rawText);
        log.info("[1/4] PdfTextCleaner tamamlandı. Temiz metin: {} karakter", cleanText.length());

        if (cleanText.isBlank()) {
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. Temizleme sonrası işlenebilir metin kalmadı.");
        }

        // Temiz metnin ilk 600 karakterini logla
        log.debug("[1/4] Temiz metin (ilk 600 karakter):\n{}",
                cleanText.substring(0, Math.min(600, cleanText.length())));

        // Adım C — Chunking: metin çok uzunsa keserek token bütçesini koru
        String inputText = cleanText;
        if (inputText.length() > MAX_INPUT_CHARS) {
            log.warn("[1/4] Metin {} karakter — {} karaktere kırpılıyor (max_tokens bütçesi korunur).",
                    inputText.length(), MAX_INPUT_CHARS);
            inputText = inputText.substring(0, MAX_INPUT_CHARS);
        }

        // Adım D — LLM
        String prompt = String.format(PROMPT_TEMPLATE, inputText);
        log.info("[2/4] LLM isteği gönderiliyor... (~{} tahmini token)",
                prompt.length() / 4);

        String jsonResponse = chatLanguageModel.generate(prompt);
        log.info("[2/4] LLM yanıtı alındı. {} karakter", jsonResponse.length());

        // LLM cevabının tamamını logla (production'da DEBUG seviyesine çek)
        log.debug("[2/4] LLM ham yanıt:\n{}", jsonResponse);

        return jsonResponse;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ana iş akışı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PDF'ten harcama DTO'larını ayıklar — saf I/O, hiç DB işlemi yapmaz.
     *
     * <p>Tüm doğrulama kontrolleri (hash, dönem çakışması) tamamlandıktan SONRA
     * DB'ye yazmak için bu metodu çağırın. Böylece bir doğrulama hatası
     * DB'deki mevcut verileri bozmaz (eski delete işleminin rollback sorunu ortadan kalkar).
     *
     * @param file Kullanıcının yüklediği PDF ekstre dosyası
     * @return Parse edilmiş TransactionDto listesi (boş olamaz — exception fırlar)
     * @throws IOException PDF okunamazsa
     * @throws IllegalArgumentException Geçerli işlem bulunamazsa
     */
    public List<TransactionDto> extractDtos(MultipartFile file) throws IOException {

        log.info("[2/4] JSON ayıklama başlıyor...");
        String rawLlmJson = extractTransactionsAsJson(file);

        // LLM bazen ```json ... ``` bloğu içinde döner — temizle
        String fencedStripped = stripMarkdownFences(rawLlmJson);

        // Türk sayı formatı düzeltici: 1.250.00 → 1250.00, 1.250,00 → 1250.00
        // Bu adım Jackson'a geçmeden önce malformed JSON number'ları onarır.
        String sanitized = sanitizeJson(fencedStripped);

        // JSON onarıcı: LLM cevabı max_tokens'ta yarıda kesilebilir.
        // Eksik kapanış parantezlerini tamamlayarak kurtarılabilir kayıtları korur.
        String cleanJson = repairJson(sanitized);
        log.info("[2/4] JSON hazır. İlk 200 karakter: {}",
                cleanJson.substring(0, Math.min(200, cleanJson.length())));

        // Fault-tolerant parse: her satır bağımsız, biri bozuk olsa diğerleri kurtarılır
        log.info("[3/4] Fault-tolerant JSON parse başlıyor...");
        List<TransactionDto> dtos;
        try {
            dtos = parseRowsFaultTolerant(cleanJson);
        } catch (Exception e) {
            // Parse tamamen başarısız — LLM'in ham çıktısını logla
            log.error("[3/4] JSON parse tamamen başarısız!\n" +
                      "── LLM ham yanıt ({} karakter) ──────────────────\n{}\n" +
                      "── Sanitize sonrası ({} karakter) ───────────────\n{}",
                    rawLlmJson.length(), rawLlmJson,
                    cleanJson.length(), cleanJson);
            throw e;
        }
        log.info("[3/4] Parse tamamlandı. {} geçerli DTO.", dtos.size());

        if (dtos.isEmpty()) {
            log.error("[3/4] Hiçbir geçerli işlem çıkarılamadı. cleanJson boyutu: {} karakter",
                    cleanJson.length());
            throw new IllegalArgumentException(
                    "Dosya formatı analiz edilemedi. PDF'te tanınan bir harcama işlemi bulunamadı. " +
                    "Lütfen geçerli bir banka ekstresi yüklediğinizden emin olun.");
        }

        return dtos;
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
     *
     * @return Geçerli DTO; zorunlu alan eksikse {@code null}
     */
    private TransactionDto mapRow(Map<String, Object> row, int index) {
        // ── Tarih ─────────────────────────────────────────────────────────────
        Object rawDate = row.get("date");
        if (rawDate == null) {
            log.debug("Satır {}: 'date' alanı null — atlandı", index);
            return null;
        }
        LocalDate date;
        try {
            date = parseDate(rawDate.toString().trim());
        } catch (DateTimeParseException e) {
            log.warn("Satır {}: tarih parse hatası '{}' — atlandı", index, rawDate);
            return null;
        }

        // ── Tutar ─────────────────────────────────────────────────────────────
        BigDecimal amount = AmountNormalizer.normalize(row.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Satır {}: geçersiz/sıfır tutar '{}' — atlandı", index, row.get("amount"));
            return null;
        }

        // ── Açıklama ──────────────────────────────────────────────────────────
        String description = row.get("description") != null
                ? row.get("description").toString().trim() : "Bilinmeyen";

        // ── Kategori ──────────────────────────────────────────────────────────
        String category = row.get("category") != null
                ? row.get("category").toString().trim() : "Diğer";

        // ── Para birimi ───────────────────────────────────────────────────────
        String currency = row.get("currency") != null
                ? row.get("currency").toString().trim().toUpperCase() : "TRY";
        if (currency.isEmpty()) currency = "TRY";

        // ── Abonelik bayrağı ──────────────────────────────────────────────────
        boolean isSubscription = false;
        Object subRaw = row.get("isSubscription");
        if (subRaw instanceof Boolean b) {
            isSubscription = b;
        } else if (subRaw != null) {
            isSubscription = Boolean.parseBoolean(subRaw.toString());
        }

        // ── Taksit alanları ───────────────────────────────────────────────────
        boolean isInstallment = false;
        Object instRaw = row.get("isInstallment");
        if (instRaw instanceof Boolean b) {
            isInstallment = b;
        } else if (instRaw != null) {
            isInstallment = Boolean.parseBoolean(instRaw.toString());
        }

        Integer currentInstallment = null;
        Object curRaw = row.get("currentInstallment");
        if (curRaw instanceof Number n) {
            currentInstallment = n.intValue();
        } else if (curRaw != null && !curRaw.toString().equalsIgnoreCase("null")) {
            try { currentInstallment = Integer.parseInt(curRaw.toString().trim()); }
            catch (NumberFormatException ignored) {}
        }

        Integer totalInstallments = null;
        Object totRaw = row.get("totalInstallments");
        if (totRaw instanceof Number n) {
            totalInstallments = n.intValue();
        } else if (totRaw != null && !totRaw.toString().equalsIgnoreCase("null")) {
            try { totalInstallments = Integer.parseInt(totRaw.toString().trim()); }
            catch (NumberFormatException ignored) {}
        }

        return new TransactionDto(date, description, amount, category, currency,
                isSubscription, isInstallment, currentInstallment, totalInstallments);
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
