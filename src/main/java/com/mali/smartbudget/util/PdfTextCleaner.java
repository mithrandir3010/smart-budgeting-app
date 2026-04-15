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
    // "STAN 987654", "TXN ID 00AB", "MRN: 1234", "SLİP NO 4567"
    private static final Pattern POS_NOISE = Pattern.compile(
        "(?i)\\b(TERMINAL|TERMİNAL|POS\\s*NO|MERCHANT\\s*NO?|MRN|RRN|STAN|" +
        "TXN\\s*(?:ID)?|SLİP\\s*NO?|ONAY\\s*(?:KODU)?|REF(?:ERENCE)?\\s*NO?" +
        ")\\s*[#:.\\-]?\\s*[A-Z0-9]{3,}",
        Pattern.CASE_INSENSITIVE
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

        text = PAGE_NUMBER   .matcher(text).replaceAll("");
        text = IBAN_LINE     .matcher(text).replaceAll("");
        text = BALANCE_LINE  .matcher(text).replaceAll("");
        text = SEPARATOR_LINE.matcher(text).replaceAll("");
        text = POS_NOISE     .matcher(text).replaceAll("");
        text = EMBEDDED_TIME .matcher(text).replaceAll(" ");
        text = EXCESS_SPACES .matcher(text).replaceAll(" ");
        text = MULTIPLE_BLANK.matcher(text).replaceAll("\n\n");

        // Tek tek satırları filtrele: anlamsız kısa satırları at
        text = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> line.length() >= 4)   // 3 karakter altı satır: gürültü
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
}
