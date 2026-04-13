package com.mali.smartbudget.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Farklı banka formatlarındaki tutar değerlerini standart {@link BigDecimal}'e çevirir.
 *
 * <h3>Desteklenen Formatlar</h3>
 * <table border="1">
 *   <tr><th>Format</th><th>Örnek</th><th>Sonuç</th><th>Açıklama</th></tr>
 *   <tr><td>Türk   </td><td>1.234,56</td><td>1234.56</td><td>Nokta=binlik, Virgül=ondalık</td></tr>
 *   <tr><td>US/ISO </td><td>1,234.56</td><td>1234.56</td><td>Virgül=binlik, Nokta=ondalık</td></tr>
 *   <tr><td>Basit-virgül</td><td>1234,56</td><td>1234.56</td><td>Sadece virgüllü ondalık</td></tr>
 *   <tr><td>Basit-nokta</td><td>1234.56</td><td>1234.56</td><td>Standard ondalık</td></tr>
 *   <tr><td>Para birimi</td><td>₺1.234,56 / TRY 89,99</td><td>1234.56 / 89.99</td><td>Sembol yok sayılır</td></tr>
 *   <tr><td>JSON number</td><td>1234.56 (Number)</td><td>1234.56</td><td>Direkt dönüşüm</td></tr>
 * </table>
 *
 * <h3>Kural</h3>
 * <ul>
 *   <li>Negatif tutarlar pozitife çevrilir (banka ekstresinde iade satırları).</li>
 *   <li>Sonuç her zaman 2 ondalık basamakta ölçeklenir.</li>
 *   <li>Parse edilemeyen değer → {@code null} (çağıran kod satırı atlayabilir).</li>
 * </ul>
 */
@Slf4j
public final class AmountNormalizer {

    private AmountNormalizer() {}

    /**
     * LLM'den veya ham metinden gelen tutarı {@link BigDecimal}'e çevirir.
     *
     * @param raw JSON map'inden alınan ham değer (Number ya da String olabilir)
     * @return Normalize edilmiş tutar; parse edilemezse {@code null}
     */
    public static BigDecimal normalize(Object raw) {
        if (raw == null) return null;

        // ── LLM zaten Number döndürdüyse — en sık durum ──────────────────────
        if (raw instanceof Number n) {
            BigDecimal bd = new BigDecimal(n.toString());
            return bd.abs().setScale(2, RoundingMode.HALF_UP);
        }

        String str = raw.toString().trim();
        if (str.isEmpty()) return null;

        // ── Para birimi sembollerini ve alfabetik karakterleri temizle ────────
        // ₺, TRY, TL, $, €, £ vb.
        String cleaned = str
                .replaceAll("(?i)[₺TL$€£]", "")
                .replaceAll("(?i)\\bTRY\\b", "")
                .replaceAll("[^0-9.,+\\-]", "")
                .trim();

        if (cleaned.isEmpty()) {
            log.warn("AmountNormalizer: '{}' → temizleme sonrası boş", raw);
            return null;
        }

        // ── İşaret ayır ───────────────────────────────────────────────────────
        boolean negative = cleaned.startsWith("-");
        if (cleaned.startsWith("+") || cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1).trim();
        }

        boolean hasDot   = cleaned.contains(".");
        boolean hasComma = cleaned.contains(",");

        // ── Separator yoksa direkt parse et ──────────────────────────────────
        // Ayırıcı yoksa kesin decimal (1234 veya 1234.56 → sadece bir nokta ile)
        if (!hasDot && !hasComma) {
            try {
                return new BigDecimal(cleaned).abs().setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException ignored) {}
        }

        if (hasDot && hasComma) {
            // Her iki ayırıcı mevcut → hangisi daha sonda geliyorsa ondalık
            int lastDot   = cleaned.lastIndexOf('.');
            int lastComma = cleaned.lastIndexOf(',');

            if (lastComma > lastDot) {
                // Türk formatı: 1.234,56 → 1234.56
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                // US formatı: 1,234.56 → 1234.56
                cleaned = cleaned.replace(",", "");
            }

        } else if (hasComma && !hasDot) {
            String[] parts = cleaned.split(",");
            // parts[1].length() <= 2 → ondalık virgülü (1234,56 veya 89,5)
            // aksi hâlde binlik virgül (1,234) → kaldır
            if (parts.length == 2 && parts[1].length() <= 2) {
                cleaned = cleaned.replace(",", ".");   // 1234,56 → 1234.56
            } else {
                cleaned = cleaned.replace(",", "");    // 1,234 → 1234
            }

        } else if (hasDot && !hasComma) {
            long dotCount = cleaned.chars().filter(c -> c == '.').count();
            if (dotCount > 1) {
                // Birden fazla nokta → hepsi binlik (1.234.567 → 1234567)
                cleaned = cleaned.replace(".", "");
            } else {
                // Tek nokta: ondalık mı yoksa binlik mi?
                String[] parts = cleaned.split("\\.");
                if (parts.length == 2 && parts[1].length() == 3) {
                    // 3 haneli son kısım → binlik (1.234 → 1234)
                    cleaned = cleaned.replace(".", "");
                }
                // Diğer: 1234.56, 89.5 → olduğu gibi bırak
            }
        }

        try {
            BigDecimal result = new BigDecimal(cleaned);
            // Ekstrelerde her tutar pozitif olmalı (iade işlemler prompt'ta atlanıyor)
            return result.abs().setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("AmountNormalizer: '{}' → parse başarısız: {}", raw, e.getMessage());
            return null;
        }
    }
}
