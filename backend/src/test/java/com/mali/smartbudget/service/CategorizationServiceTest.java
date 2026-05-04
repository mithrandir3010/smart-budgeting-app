package com.mali.smartbudget.service;

import com.mali.smartbudget.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategorizationService — Birim Testleri")
class CategorizationServiceTest {

    private final CategorizationService service = new CategorizationService();

    // =========================================================================
    // LLM Label Eşleştirme — birincil strateji
    // =========================================================================

    @Nested
    @DisplayName("LLM label eşleştirme")
    class LlmLabelMapping {

        @Test
        @DisplayName("Market → FOOD")
        void market_returnsFood() {
            assertThat(service.categorize("Migros", "Market")).isEqualTo(Category.FOOD);
        }

        @Test
        @DisplayName("Kafe → FOOD")
        void kafe_returnsFood() {
            assertThat(service.categorize("Starbucks", "Kafe")).isEqualTo(Category.FOOD);
        }

        @Test
        @DisplayName("Restoran → FOOD")
        void restoran_returnsFood() {
            assertThat(service.categorize("Burger King", "Restoran")).isEqualTo(Category.FOOD);
        }

        @Test
        @DisplayName("Ulaşım → TRANSPORT")
        void ulasim_returnsTransport() {
            assertThat(service.categorize("İETT", "Ulaşım")).isEqualTo(Category.TRANSPORT);
        }

        @Test
        @DisplayName("Akaryakıt → TRANSPORT")
        void akaryakit_returnsTransport() {
            assertThat(service.categorize("Shell", "Akaryakıt")).isEqualTo(Category.TRANSPORT);
        }

        @Test
        @DisplayName("Kira → HOUSING")
        void kira_returnsHousing() {
            assertThat(service.categorize("Ev Kirası", "Kira")).isEqualTo(Category.HOUSING);
        }

        @Test
        @DisplayName("Fatura → HOUSING")
        void fatura_returnsHousing() {
            assertThat(service.categorize("Turkcell", "Fatura")).isEqualTo(Category.HOUSING);
        }

        @Test
        @DisplayName("Giyim → SHOPPING")
        void giyim_returnsShopping() {
            assertThat(service.categorize("Zara", "Giyim")).isEqualTo(Category.SHOPPING);
        }

        @Test
        @DisplayName("Teknoloji → SHOPPING")
        void teknoloji_returnsShopping() {
            assertThat(service.categorize("Samsung", "Teknoloji")).isEqualTo(Category.SHOPPING);
        }

        @Test
        @DisplayName("Sağlık → HEALTH")
        void saglik_returnsHealth() {
            assertThat(service.categorize("Eczane", "Sağlık")).isEqualTo(Category.HEALTH);
        }

        @Test
        @DisplayName("Eğitim → EDUCATION")
        void egitim_returnsEducation() {
            assertThat(service.categorize("Udemy", "Eğitim")).isEqualTo(Category.EDUCATION);
        }

        @Test
        @DisplayName("Eğlence → ENTERTAINMENT")
        void eglence_returnsEntertainment() {
            assertThat(service.categorize("Netflix", "Eğlence")).isEqualTo(Category.ENTERTAINMENT);
        }

        @Test
        @DisplayName("Sigorta → ENTERTAINMENT")
        void sigorta_returnsEntertainment() {
            assertThat(service.categorize("Allianz", "Sigorta")).isEqualTo(Category.ENTERTAINMENT);
        }

        @Test
        @DisplayName("Diğer → OTHER")
        void diger_returnsOther() {
            assertThat(service.categorize("Bilinmeyen", "Diğer")).isEqualTo(Category.OTHER);
        }

        @Test
        @DisplayName("Bilinmeyen LLM label → keyword fallback devreye girer")
        void unknownLlmLabel_fallsBackToKeyword() {
            assertThat(service.categorize("Migros", "BilinmeyenKategori"))
                    .isEqualTo(Category.FOOD);
        }
    }

    // =========================================================================
    // Keyword Fallback — LLM label null veya tanımsız
    // =========================================================================

    @Nested
    @DisplayName("Keyword fallback")
    class KeywordFallback {

        @Test
        @DisplayName("null LLM label + 'migros' açıklama → FOOD")
        void nullLabel_migros_returnsFood() {
            assertThat(service.categorize("Migros Şişli", null)).isEqualTo(Category.FOOD);
        }

        @Test
        @DisplayName("null LLM label + 'netflix' açıklama → ENTERTAINMENT")
        void nullLabel_netflix_returnsEntertainment() {
            assertThat(service.categorize("Netflix.com", null)).isEqualTo(Category.ENTERTAINMENT);
        }

        @Test
        @DisplayName("null LLM label + 'shell' açıklama → TRANSPORT")
        void nullLabel_shell_returnsTransport() {
            assertThat(service.categorize("Shell Benzin", null)).isEqualTo(Category.TRANSPORT);
        }

        @Test
        @DisplayName("null LLM label + 'eczane' açıklama → HEALTH")
        void nullLabel_eczane_returnsHealth() {
            assertThat(service.categorize("Eczane Merkez", null)).isEqualTo(Category.HEALTH);
        }

        @Test
        @DisplayName("null açıklama ve null label → OTHER")
        void nullDescriptionAndLabel_returnsOther() {
            assertThat(service.categorize(null, null)).isEqualTo(Category.OTHER);
        }

        @Test
        @DisplayName("Tanımsız açıklama → OTHER")
        void unknownDescription_returnsOther() {
            assertThat(service.categorize("XYZ12345 FOOBAR", null)).isEqualTo(Category.OTHER);
        }
    }

    // =========================================================================
    // Sınır Durumlar
    // =========================================================================

    @Nested
    @DisplayName("Sınır durumlar")
    class EdgeCases {

        @Test
        @DisplayName("Boş string LLM label → keyword fallback devreye girer")
        void emptyLlmLabel_fallsBackToKeyword() {
            assertThat(service.categorize("BİM Market", "")).isEqualTo(Category.FOOD);
        }

        @Test
        @DisplayName("Boş string açıklama ve geçerli LLM label → LLM label kullanılır")
        void emptyDescription_validLabel_usesLabel() {
            assertThat(service.categorize("", "Sağlık")).isEqualTo(Category.HEALTH);
        }

        @Test
        @DisplayName("Büyük-küçük harf duyarsız keyword eşleştirme: 'MIGROS' → FOOD")
        void caseInsensitiveKeyword_returnsFood() {
            assertThat(service.categorize("MIGROS LEVENT", null)).isEqualTo(Category.FOOD);
        }
    }
}
