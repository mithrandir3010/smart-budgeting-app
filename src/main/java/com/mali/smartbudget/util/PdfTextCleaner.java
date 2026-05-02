package com.mali.smartbudget.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Banka ekstresinden çıkarılan ham PDF metnini LLM'e göndermeden önce temizler.
 *
 * <h3>Temizlenen Gürültüler</h3>
 * <ul>
 *   <li>Sayfa numaraları: "Sayfa 1/5", "Page 2 of 10"</li>
 *   <li>Tekrarlayan başlık/alt bilgi satırları (banka adı, IBAN, tarih aralığı)</li>
 *   <li>Ayırıcı çizgiler: --- === ___ gibi</li>
 *   <li>POS terminal/referans bilgileri: "TERMINAL 00123456", "REF: ABC123"</li>
 *   <li>İşlem içine gömülü saatler: "14:32", "09:05:47"</li>
 *   <li>Aşırı boşluk ve art arda boş satırlar</li>
 * </ul>
 *
 * <p>Kullanım:
 * <pre>
 *   String cleaned = PdfTextCleaner.clean(rawPdfText);
 * </pre>
 */
@Slf4j
public final class PdfTextCleaner {

    private PdfTextCleaner() {}

    // ── Sayfa numarası satırları ──────────────────────────────────────────────
    // "Sayfa 1/5", "Sayfa: 1 / 5", "Page 2 of 10"
    // NOT: "^\d+/\d+$" pattern'ı KALDIRILDI — taksit fraksiyonlarını (2/3, 1/6)
    // yanlışlıkla siliyordu. Sayfa numaralarını bağlamsal kelimeyle yakala.
    private static final Pattern PAGE_NUMBER = Pattern.compile(
        "(?im)^.*\\bsayfa\\s*:?\\s*\\d+\\s*/\\s*\\d+.*$" +
        "|^.*\\bpage\\s+\\d+\\s+of\\s+\\d+.*$"
    );

    // ── Ayırıcı / dekoratif çizgiler ─────────────────────────────────────────
    // En az 5 tekrarlayan karakter: ---, ===, ___, ***
    private static final Pattern SEPARATOR_LINE = Pattern.compile(
        "(?m)^[\\-=_*~.]{5,}\\s*$"
    );

    // ── POS / terminal / referans gürültüsü ──────────────────────────────────
    // "TERMINAL 00123456", "POS NO: ABC", "REF: XYZ123", "ONAY: 123456"
    // "TXN ID 00AB", "MRN: 1234", "SLİP NO 4567"
    // NOT: STAN kaldırıldı — "İSTANBUL" içindeki "STANBUL"'u yanlışlıkla siliyordu.
    private static final Pattern POS_NOISE = Pattern.compile(
        "(?i)\\b(TERMINAL|TERMİNAL|POS\\s*NO|MERCHANT\\s*NO?|MRN|RRN|" +
        "TXN\\s*(?:ID)?|SLİP\\s*NO?|ONAY\\s*(?:KODU)?|REF(?:ERENCE)?\\s*NO?" +
        ")\\s*[#:.\\-]?\\s*[A-Z0-9]{3,}",
        Pattern.CASE_INSENSITIVE
    );

    // ── Halkbank (Paraf) gürültü satırları ───────────────────────────────────
    // Hesaptan ödeme satırları, taksit kredileri, tablo başlıkları
    // UNICODE_CASE: Türkçe Ö/ö, Ü/ü gibi karakterlerin büyük/küçük eşleşmesi için gerekli
    private static final Pattern HALKBANK_NOISE = Pattern.compile(
        "hesaptan\\s+.deme|sonradantak\\.alacak|ekstre\\s+borcu|" +
        "parafpara\\s+bilgileriniz|faiz\\s+oranlar|" +
        "^-\\s*\\+\\s*\\d|^\\+\\s*\\d{1,3}[,.]",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── İş Bankası (Maximum) gürültü satırları ───────────────────────────────
    // Fatura servis ücreti, hesaptan aktarım, teşekkür/toplam satırları, MaxiPuan ilave
    private static final Pattern ISBANK_NOISE = Pattern.compile(
        "fatura\\s+.deme\\s+.creti|hesaptan\\s+aktarim|" +
        "maxipuan\\s+ilave|.demeleriniz\\s+i.in\\s+te.ekk.r|" +
        "taksitli\\s+bor.\\s+toplam|bir\\s+.nceki\\s+hesap\\s..zeti|" +
        "^\\*{3,}|aylik\\s+taksitli|kalan\\s+taksitli",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── Yapı Kredi (WorldCard) gürültü satırları ──────────────────────────────
    // Temassız etiket, WorldPuan özeti, banka başlığı, sayfa bilgisi, ödeme satırları, faiz satırları
    private static final Pattern YAPIKREDI_NOISE = Pattern.compile(
        "temassız|worldpuan|taksitlendirme\\s+i.lem\\s+faizi|" +
        ".deme-.nternet|yapi\\s+ve\\s+kredi|büyük\\s+mükellef|s.ra\\s+no:|" +
        "puan\\s+.zeti|mesaj.n.z|i.lem\\s+tarihi\\s+i.lemler|" +
        "^toplam\\s|^\\d+\\s*/\\s*\\d+\\s+\\d+\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // ── İşlem içine gömülü saatler ───────────────────────────────────────────
    // "14:32", "09:05:47" — tarih olmayan zaman damgaları
    private static final Pattern EMBEDDED_TIME = Pattern.compile(
        "\\b([01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?\\b"
    );

    // ── Tekrarlayan boşluklar (satır içi) ────────────────────────────────────
    private static final Pattern EXCESS_SPACES = Pattern.compile("[ \\t]{3,}");

    // ── 3+ art arda boş satır → 2 satıra indir ───────────────────────────────
    private static final Pattern MULTIPLE_BLANK = Pattern.compile("\\n{3,}");

    // ── IBAN / hesap numarası satırları ──────────────────────────────────────
    // TR + 24 rakam veya "IBAN:" ile başlayan satırlar
    private static final Pattern IBAN_LINE = Pattern.compile(
        "(?im)^.*\\bIBAN\\b.*$|^.*\\bTR\\d{24}\\b.*$"
    );

    // ── Bakiye / limit bilgisi satırları ─────────────────────────────────────
    // "Kullanılabilir Limit", "Mevcut Bakiye", "Borç Bakiyesi"
    private static final Pattern BALANCE_LINE = Pattern.compile(
        "(?im)^.*(kullanılabilir\\s+limit|mevcut\\s+bakiye|borç\\s+bakiye|" +
        "available\\s+balance|credit\\s+limit|statement\\s+balance).*$"
    );

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ham PDF metnini temizler.
     *
     * @param rawText PDFTextStripper'dan gelen ham metin
     * @return LLM'e gönderilmeye hazır, gürültüsü azaltılmış metin
     */
    public static String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";

        int originalLength = rawText.length();
        String text = rawText;

        // Banka tespiti — gürültü filtresi için kullanılır
        boolean isHalkbank  = text.contains("HALKBANK") || text.contains("paraf.com.tr")
                              || text.contains("Halk Bankası");
        boolean isIsbank    = text.contains("isbank.com.tr") || text.contains("MaxiPuan")
                              || text.contains("MAXIPUAN");
        boolean isYapiKredi = text.contains("YAPI ve KREDİ BANKASI") || text.contains("worldcard.com.tr")
                              || text.contains("WORLDPUAN");

        text = PAGE_NUMBER   .matcher(text).replaceAll("");
        text = IBAN_LINE     .matcher(text).replaceAll("");
        text = BALANCE_LINE  .matcher(text).replaceAll("");
        text = SEPARATOR_LINE.matcher(text).replaceAll("");
        text = POS_NOISE     .matcher(text).replaceAll("");
        text = EMBEDDED_TIME .matcher(text).replaceAll(" ");
        text = EXCESS_SPACES .matcher(text).replaceAll("  ");
        text = MULTIPLE_BLANK.matcher(text).replaceAll("\n\n");

        // Tek tek satırları filtrele: anlamsız kısa satırları ve banka gürültüsünü at.
        // İstisna: "2/3", "1/6" gibi taksit fraksiyonu içeren satırları koru.
        final boolean halkbank  = isHalkbank;
        final boolean isbank    = isIsbank;
        final boolean yapikredi = isYapiKredi;
        text = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> line.length() >= 4 || line.matches("\\d{1,2}/\\d{1,2}"))
                .filter(line -> !isBankNoiseLine(line, halkbank, isbank, yapikredi))
                .collect(Collectors.joining("\n"));

        text = text.trim();

        int removedChars = originalLength - text.length();

        double removedPct = originalLength > 0
                ? (removedChars * 100.0 / originalLength) : 0;

        log.info("PdfTextCleaner: {} → {} karakter (-%d karakter, %%{} temizlendi)"
                .formatted(originalLength, text.length()),
                removedChars, "%.1f".formatted(removedPct));

        return text;
    }

    /**
     * Banka formatına özgü gürültü satırlarını tespit eder.
     * Hesap özeti satırları, servis ücretleri, ödeme satırları bu filtreyle elenir.
     */
    private static boolean isBankNoiseLine(String line, boolean isHalkbank,
                                           boolean isIsbank, boolean isYapiKredi) {
        if (isHalkbank  && HALKBANK_NOISE  .matcher(line).find()) return true;
        if (isIsbank    && ISBANK_NOISE    .matcher(line).find()) return true;
        if (isYapiKredi && YAPIKREDI_NOISE .matcher(line).find()) return true;
        return false;
    }
}
