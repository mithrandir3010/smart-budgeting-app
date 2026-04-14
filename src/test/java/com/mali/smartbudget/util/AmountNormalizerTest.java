package com.mali.smartbudget.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AmountNormalizer birim testleri.
 *
 * <p>Kapsam: null/boş, Number tipleri, para birimi sembolleri, Türk/US/basit formatlar,
 * binlik ve ondalık ayırıcı tespiti, işaret (+/-), ölçekleme (scale=2).
 */
@DisplayName("AmountNormalizer — Birim Testleri")
class AmountNormalizerTest {

    // =========================================================================
    // null / boş / geçersiz girdi
    // =========================================================================

    @Test
    @DisplayName("null → null döner")
    void normalize_null_returnsNull() {
        assertThat(AmountNormalizer.normalize(null)).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → null")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Boş veya yalnızca boşluktan oluşan String → null döner")
    void normalize_emptyOrBlank_returnsNull(String input) {
        assertThat(AmountNormalizer.normalize(input)).isNull();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → null")
    @ValueSource(strings = {"abc", "xyz", "---", "???", "N/A"})
    @DisplayName("Hiç sayısal karakter içermeyen metin → null döner")
    void normalize_nonNumericText_returnsNull(String input) {
        assertThat(AmountNormalizer.normalize(input)).isNull();
    }

    // =========================================================================
    // Number tipi — LLM'den gelen JSON sayıları
    // =========================================================================

    @Test
    @DisplayName("Integer (1234) → 1234.00")
    void normalize_integer_returnsTwoDecimalPlaces() {
        assertThat(AmountNormalizer.normalize(1234)).isEqualByComparingTo("1234.00");
    }

    @Test
    @DisplayName("Double (1234.5) → 1234.50")
    void normalize_double_roundsToTwoDecimalPlaces() {
        assertThat(AmountNormalizer.normalize(1234.5)).isEqualByComparingTo("1234.50");
    }

    @Test
    @DisplayName("Long (1_000_000) → 1000000.00")
    void normalize_long_returnsBigDecimal() {
        assertThat(AmountNormalizer.normalize(1_000_000L)).isEqualByComparingTo("1000000.00");
    }

    @Test
    @DisplayName("Negatif Number (-500.0) → mutlak değer 500.00")
    void normalize_negativeNumber_returnsAbsoluteValue() {
        assertThat(AmountNormalizer.normalize(-500.0)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Sıfır (0) → 0.00")
    void normalize_zero_returnsZero() {
        assertThat(AmountNormalizer.normalize(0)).isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // Para birimi sembolleri
    // =========================================================================

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource({
        "₺1.234,56,  1234.56",
        "TRY 89,99,  89.99",
        "TL 100,00,  100.00",
        "$1.234,56,  1234.56",
        "€1.234,56,  1234.56",
        "£1.234,56,  1234.56",
    })
    @DisplayName("Para birimi sembolleri soyulur, kalan tutar normalize edilir")
    void normalize_currencySymbols_strippedAndNormalized(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // Türk formatı — nokta=binlik, virgül=ondalık
    // =========================================================================

    @ParameterizedTest(name = "[{index}] Türk: \"{0}\" → {1}")
    @CsvSource({
        "1.234,56,       1234.56",
        "12.500,00,      12500.00",
        "1.000.000,00,   1000000.00",
        "245,90,         245.90",
    })
    @DisplayName("Türk formatı (nokta=binlik, virgül=ondalık) doğru parse edilir")
    void normalize_turkishFormat_parsedCorrectly(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // US/ISO formatı — virgül=binlik, nokta=ondalık
    // =========================================================================

    @ParameterizedTest(name = "[{index}] US: \"{0}\" → {1}")
    @CsvSource({
        "1.234,56,     1234.56",   // (same as Turkish in dual-separator case)
        "1234.56,      1234.56",
        "12500.00,     12500.00",
    })
    @DisplayName("US/ISO formatı (virgül=binlik, nokta=ondalık) doğru parse edilir")
    void normalize_usFormat_parsedCorrectly(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // Sadece virgüllü ondalık — 1234,56
    // =========================================================================

    @ParameterizedTest(name = "[{index}] Virgüllü: \"{0}\" → {1}")
    @CsvSource({
        "1234,56,   1234.56",
        "89,50,     89.50",
        "0,99,      0.99",
        "89,5,      89.50",    // tek ondalık basamak
    })
    @DisplayName("Sadece virgüllü ondalık format doğru parse edilir")
    void normalize_plainCommaDecimal_parsedCorrectly(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // Sadece noktalı ondalık — 1234.56
    // =========================================================================

    @ParameterizedTest(name = "[{index}] Noktalı: \"{0}\" → {1}")
    @CsvSource({
        "1234.56,   1234.56",
        "89.50,     89.50",
        "0.99,      0.99",
        "100.00,    100.00",
    })
    @DisplayName("Standart ondalık (nokta) format doğru parse edilir")
    void normalize_plainDotDecimal_parsedCorrectly(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // Binlik ayırıcı tespiti
    // =========================================================================

    @Test
    @DisplayName("Tek nokta + 3 haneli kısım (1.234) → binlik ayırıcı → 1234.00")
    void normalize_singleDotThreeDigits_treatedAsThousandSeparator() {
        assertThat(AmountNormalizer.normalize("1.234")).isEqualByComparingTo("1234.00");
    }

    @Test
    @DisplayName("Birden fazla nokta (1.234.567) → tüm noktalar binlik → 1234567.00")
    void normalize_multipleDotsAsThousands_removedCorrectly() {
        assertThat(AmountNormalizer.normalize("1.234.567")).isEqualByComparingTo("1234567.00");
    }

    // =========================================================================
    // İşaret (+/-)
    // =========================================================================

    @Test
    @DisplayName("Negatif tutar (-500,00) → mutlak değer 500.00 (banka iadesi)")
    void normalize_negativeAmountString_returnsPositive() {
        assertThat(AmountNormalizer.normalize("-500,00")).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Pozitif işaretli tutar (+250,50) → 250.50")
    void normalize_positiveSignString_parsedCorrectly() {
        assertThat(AmountNormalizer.normalize("+250,50")).isEqualByComparingTo("250.50");
    }

    // =========================================================================
    // Tam sayılar (ayırıcı yok)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
    @CsvSource({
        "1234,  1234.00",
        "0,     0.00",
        "100,   100.00",
    })
    @DisplayName("Ayırıcısız tam sayı → 2 ondalık basamakla normalize edilir")
    void normalize_plainInteger_returns2DecimalPlaces(String input, String expected) {
        assertThat(AmountNormalizer.normalize(input)).isEqualByComparingTo(expected);
    }

    // =========================================================================
    // Scale garantisi
    // =========================================================================

    @Test
    @DisplayName("Sonuç her zaman scale=2 ile döner")
    void normalize_anyInput_returnsScale2() {
        BigDecimal result = AmountNormalizer.normalize("100");
        assertThat(result).isNotNull();
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Number tipinde scale=2 garantisi")
    void normalize_numberType_returnsScale2() {
        BigDecimal result = AmountNormalizer.normalize(999);
        assertThat(result).isNotNull();
        assertThat(result.scale()).isEqualTo(2);
    }
}
