package com.mali.smartbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.util.AmountNormalizer;
import com.mali.smartbudget.util.PdfTextCleaner;
import dev.langchain4j.model.chat.ChatLanguageModel;
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

/**
 * PDF ekstreden işlem (Transaction) verilerini LLM aracılığıyla ayıklayan ve kaydeden servis.
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
 * <h3>Mock Modu</h3>
 * <ul>
 *   <li>{@code serena.extraction.mock=true} → Her zaman sahte veri, LLM çağrısı yok.</li>
 *   <li>Geçersiz/eksik {@code OPENAI_API_KEY} → Otomatik mock moda geçer.</li>
 * </ul>
 *
 * <h3>Fault-Tolerant Parsing</h3>
 * LLM'den dönen JSON içindeki tek bir bozuk satır tüm süreci patlatmaz.
 * Her satır bağımsız parse edilir; başarısız satırlar loglanıp atlanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private static final String DEFAULT_API_KEY = "your-api-key-here";

    // ── Mock veri — abonelikler dahil ─────────────────────────────────────────
    private static final String MOCK_JSON = """
            [
              {"date": "2026-04-01", "description": "Migros Market",       "amount": 245.90, "category": "Market",     "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-03", "description": "Starbucks",            "amount": 89.50,  "category": "Kafe",       "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-05", "description": "Kira Ödemesi",         "amount": 12000.00,"category": "Kira",      "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-07", "description": "Netflix",              "amount": 79.99,  "category": "Eğlence",   "currency": "TRY", "isSubscription": true},
              {"date": "2026-04-08", "description": "Spotify Premium",      "amount": 49.99,  "category": "Eğlence",   "currency": "TRY", "isSubscription": true},
              {"date": "2026-04-10", "description": "iCloud Depolama 50GB", "amount": 14.99,  "category": "Teknoloji", "currency": "TRY", "isSubscription": true}
            ]
            """;

    /**
     * Türk bankası ekstrelerine (İş Bankası, Ziraat, Garanti, Yapı Kredi vb.) özel
     * LLM prompt'u.
     *
     * <p>Temel özellikler:
     * <ul>
     *   <li>dd.MM.yyyy tarih formatı açıkça belgelenmiş</li>
     *   <li>1.234,56 Türk tutar formatı → LLM standart JSON number üretir</li>
     *   <li>Türkçe karakter (ğ, ş, ı, ç, ö, ü) korunur</li>
     *   <li>Kategori listesi sabit — LLM serbest yazmıyor</li>
     *   <li>POS/terminal/referans gürültüsü kural olarak tanımlanmış</li>
     * </ul>
     */
    private static final String PROMPT_TEMPLATE = """
            Aşağıdaki Türk bankası ekstre metni (İş Bankası, Ziraat, Garanti, Yapı Kredi vb.) analiz edilecek.
            Harcama işlemlerini JSON dizisi olarak döndür.

            ÇIKTI FORMATI — kesinlikle bu yapıda, başka hiçbir metin EKLEME:
            [
              {
                "date": "YYYY-MM-DD",
                "description": "Firma Adı",
                "amount": 1234.56,
                "category": "Kategori",
                "currency": "TRY",
                "isSubscription": false
              }
            ]

            ── TARİH KURALLARI (date) ────────────────────────────────────────────────
            • Çıkışta HER ZAMAN YYYY-MM-DD formatı kullan (ISO 8601)
            • Türk bankalarında giriş formatı genellikle: 15.03.2026 veya 15/03/2026
              Bu formatları YYYY-MM-DD'ye dönüştür: 15.03.2026 → "2026-03-15"
            • Tarih okunamazsa o satırı tamamen atla

            ── TUTAR KURALLARI (amount) ──────────────────────────────────────────────
            • Çıkışta JSON sayısı olarak yaz — string değil, tırnak işareti yok
            • Ondalık ayırıcı NOKTA: 1234.56 ✓   Tırnaklı string: "1234.56" ✗
            • Türk formatı (1.234,56 TL) → JSON sayısına çevir: 1234.56
            • Binlik ayırıcı nokta/virgülü çıkışa YAZMA
            • Her zaman POZİTİF sayı — negatif tutar kullanma
            • İade / iade alacak / geri ödeme satırlarını ATLA
            • Tutar okunamazsa o satırı tamamen atla

            ── AÇIKLAMA KURALLARI (description) ─────────────────────────────────────
            • Yalnızca firma/mağaza adını yaz
            • Türkçe karakterleri KORU: ğ, ş, ı, ç, ö, ü — bunları değiştirme
              Örnek: "ŞAŞMAZ MARKET" → "Şaşmaz Market"  ✓
                     "TASARIM GÜZEL" → "Tasarım Güzel"  ✓
            • Şu bilgileri YAZMA: POS ID, terminal no, saat, ref no, onay kodu,
              slip no, merchant ID, işlem no
            • Kısaltmaları açıkla: "MKT" → "Market", "REST" → "Restoran",
              "PETROL" → "Akaryakıt İstasyonu", "ELK" → "Elektrik"
            • Büyük harfi markalaştır: "MIGROS AVM CANKAYA" → "Migros"
            • Şehir/şube/AVM bilgisini kaldır: "Carrefour Ankara" → "Carrefour"

            ── KATEGORİ SEÇENEKLERİ (category) ─────────────────────────────────────
            Yalnızca bu listeden seç — başka kelime yazma:
            Market, Kafe, Restoran, Ulaşım, Akaryakıt, Fatura, Kira, Sağlık,
            Eğlence, Teknoloji, Giyim, Eğitim, Sigorta, Diğer

            ── isSubscription KURALLARI ──────────────────────────────────────────────
            true  → Netflix, Spotify, YouTube Premium, Apple TV+, Disney+, Amazon Prime,
                    iCloud, Google One, Dropbox, OneDrive, Gym üyeliği, dijital dergi,
                    Adobe CC, Microsoft 365, antivirüs/güvenlik lisansı, IPTV aboneliği
            false → Tek seferlik alışveriş, market, kafe, restoran, ulaşım, kira vb.

            ── ATLAMA KURALLARI ──────────────────────────────────────────────────────
            • Bakiye satırları: "Kullanılabilir Limit", "Mevcut Bakiye", "Borç Toplamı"
            • IBAN / hesap numarası satırları
            • Sayfa başlık ve alt bilgileri (banka adı, tarih aralığı, müşteri no)
            • KDV / komisyon / faiz satırları (harcama değil, banka ücreti)
            • Para yatırma / havale gelen / EFT gelen (harcama değil)

            ── ÖRNEKLER (Türk bankası formatından JSON'a) ───────────────────────────
            Ham metin: "15.03.2026  MIGROS  1.250,00 TL"
            Çıktı: {"date":"2026-03-15","description":"Migros","amount":1250.00,"category":"Market","currency":"TRY","isSubscription":false}

            Ham metin: "01.04.2026  NETFLIX.COM  149,90 TL"
            Çıktı: {"date":"2026-04-01","description":"Netflix","amount":149.90,"category":"Eğlence","currency":"TRY","isSubscription":true}

            Ham metin: "10.04.2026  SHELL AKARYAKIT  850,50 TL"
            Çıktı: {"date":"2026-04-10","description":"Shell","amount":850.50,"category":"Akaryakıt","currency":"TRY","isSubscription":false}

            Ekstre metni:
            %s
            """;

    // Tarih formatları (LLM bazen farklı format döner — tümünü destekle)
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2026-04-05
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),          // 05.04.2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),          // 05/04/2026
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),          // 2026/04/05
            DateTimeFormatter.ofPattern("d.M.yyyy"),            // 5.4.2026
            DateTimeFormatter.ofPattern("dd-MM-yyyy")           // 05-04-2026
    );

    @Value("${serena.extraction.mock:false}")
    private boolean forceMockMode;

    @Value("${langchain4j.open-ai.chat-model.api-key:" + DEFAULT_API_KEY + "}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Mock modu kontrolü
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isMockMode() {
        if (forceMockMode) return true;
        if (openAiApiKey == null || openAiApiKey.isBlank()) return true;
        if (DEFAULT_API_KEY.equals(openAiApiKey)) return true;
        if (!openAiApiKey.startsWith("sk-")) return true;
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLM / Mock JSON üretimi
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PDF dosyasını okur; Mock Mode'da sahte JSON, aksi hâlde LLM yanıtı döner.
     *
     * <p>Pre-processing adımı burada uygulanır:
     * <ol>
     *   <li>PDF → ham metin ({@link PdfService})</li>
     *   <li>Ham metin → temiz metin ({@link PdfTextCleaner})</li>
     *   <li>Temiz metin → prompt → LLM → JSON</li>
     * </ol>
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        boolean mock = isMockMode();
        log.info(">>> Extraction mode: {} | forceMock={} | apiKey={}",
                mock ? "MOCK" : "LLM", forceMockMode,
                openAiApiKey != null && openAiApiKey.length() > 6
                        ? openAiApiKey.substring(0, 6) + "…" : "(kısa/null)");

        if (mock) {
            log.warn("[1/4] MOCK MODE: Sahte harcamalar kullanılıyor.");
            return MOCK_JSON;
        }

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

        // Adım C — LLM
        String prompt = String.format(PROMPT_TEMPLATE, cleanText);
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
        String json = extractTransactionsAsJson(file);

        // LLM bazen ```json ... ``` bloğu içinde döner — temizle
        String cleanJson = stripMarkdownFences(json);
        log.info("[2/4] JSON temizlendi. İlk 120 karakter: {}",
                cleanJson.substring(0, Math.min(120, cleanJson.length())));

        // Fault-tolerant parse: her satır bağımsız, biri bozuk olsa diğerleri kurtarılır
        log.info("[3/4] Fault-tolerant JSON parse başlıyor...");
        List<TransactionDto> dtos = parseRowsFaultTolerant(cleanJson);
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

        return new TransactionDto(date, description, amount, category, currency, isSubscription);
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
}
