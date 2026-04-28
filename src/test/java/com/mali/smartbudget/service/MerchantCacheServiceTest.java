package com.mali.smartbudget.service;

import com.mali.smartbudget.model.MerchantCache;
import com.mali.smartbudget.repository.MerchantCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantCacheService — Birim Testleri")
class MerchantCacheServiceTest {

    @Mock
    private MerchantCacheRepository repository;

    @InjectMocks
    private MerchantCacheService service;

    private MerchantCache migros;
    private MerchantCache netflix;

    @BeforeEach
    void setUp() {
        migros = MerchantCache.builder()
                .id(1L).pattern("Migros").category("Market").subscription(false).hitCount(5).build();
        netflix = MerchantCache.builder()
                .id(2L).pattern("Netflix").category("Eğlence").subscription(true).hitCount(3).build();
    }

    // =========================================================================
    // lookup — Tam Eşleme
    // =========================================================================

    @Nested
    @DisplayName("Tam Eşleme")
    class ExactMatch {

        @Test
        @DisplayName("Birebir aynı pattern → CachedResult döner")
        void lookup_exactMatch_returnsCachedResult() {
            when(repository.findAll()).thenReturn(List.of(migros, netflix));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("Migros");

            assertThat(result).isPresent();
            assertThat(result.get().category()).isEqualTo("Market");
            assertThat(result.get().isSubscription()).isFalse();
        }

        @Test
        @DisplayName("Case-insensitive eşleme — 'MIGROS' cache'de 'Migros' bulur")
        void lookup_caseInsensitive_matches() {
            when(repository.findAll()).thenReturn(List.of(migros));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("MIGROS");

            assertThat(result).isPresent();
            assertThat(result.get().category()).isEqualTo("Market");
        }

        @Test
        @DisplayName("Türkçe İ içeren pattern — 'BİM' doğru normalize edilir")
        void lookup_turkishDottedI_normalized() {
            MerchantCache bim = MerchantCache.builder()
                    .id(3L).pattern("BİM").category("Market").subscription(false).hitCount(0).build();
            when(repository.findAll()).thenReturn(List.of(bim));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("BİM");

            assertThat(result).isPresent();
            assertThat(result.get().category()).isEqualTo("Market");
        }

        @Test
        @DisplayName("Abonelik bayrağı doğru döner — Netflix isSubscription=true")
        void lookup_netflix_isSubscriptionTrue() {
            when(repository.findAll()).thenReturn(List.of(migros, netflix));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("Netflix");

            assertThat(result).isPresent();
            assertThat(result.get().isSubscription()).isTrue();
            assertThat(result.get().category()).isEqualTo("Eğlence");
        }
    }

    // =========================================================================
    // lookup — Pattern Eşleme
    // =========================================================================

    @Nested
    @DisplayName("Pattern Eşleme")
    class PatternMatch {

        @Test
        @DisplayName("Cache'deki 'Netflix' → 'Netflix.com' description'ında bulunur")
        void lookup_patternInDescription_matches() {
            when(repository.findAll()).thenReturn(List.of(netflix));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("Netflix.com");

            assertThat(result).isPresent();
            assertThat(result.get().category()).isEqualTo("Eğlence");
        }

        @Test
        @DisplayName("Cache'deki 'Migros Nişantaşı' → 'Migros' description'ında da eşleşir")
        void lookup_descriptionInPattern_matches() {
            MerchantCache migrosNisantasi = MerchantCache.builder()
                    .id(4L).pattern("Migros Nişantaşı").category("Market").subscription(false).build();
            when(repository.findAll()).thenReturn(List.of(migrosNisantasi));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("Migros");

            assertThat(result).isPresent();
            assertThat(result.get().category()).isEqualTo("Market");
        }

        @Test
        @DisplayName("Hiçbir pattern eşleşmiyorsa → empty döner")
        void lookup_noMatch_returnsEmpty() {
            when(repository.findAll()).thenReturn(List.of(migros, netflix));

            Optional<MerchantCacheService.CachedResult> result = service.lookup("XYZ Bilinmeyen Yer");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null description → empty döner")
        void lookup_nullDescription_returnsEmpty() {
            Optional<MerchantCacheService.CachedResult> result = service.lookup(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Boş string description → empty döner")
        void lookup_blankDescription_returnsEmpty() {
            Optional<MerchantCacheService.CachedResult> result = service.lookup("   ");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // lookup — Hit Count
    // =========================================================================

    @Nested
    @DisplayName("Hit Count")
    class HitCount {

        @Test
        @DisplayName("Eşleşen entry'nin hitCount'u artırılır")
        void lookup_hit_incrementsHitCount() {
            when(repository.findAll()).thenReturn(List.of(migros));

            service.lookup("Migros");

            ArgumentCaptor<MerchantCache> captor = ArgumentCaptor.forClass(MerchantCache.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getHitCount()).isEqualTo(6); // 5 + 1
        }
    }

    // =========================================================================
    // learn — Yeni Merchant Kaydetme
    // =========================================================================

    @Nested
    @DisplayName("learn — Öğrenme")
    class Learn {

        @Test
        @DisplayName("Yeni merchant → save çağrılır")
        void learn_newMerchant_saves() {
            when(repository.findAll()).thenReturn(List.of());

            service.learn("Shell", "Akaryakıt", false);

            ArgumentCaptor<MerchantCache> captor = ArgumentCaptor.forClass(MerchantCache.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getPattern()).isEqualTo("Shell");
            assertThat(captor.getValue().getCategory()).isEqualTo("Akaryakıt");
            assertThat(captor.getValue().isSubscription()).isFalse();
        }

        @Test
        @DisplayName("Zaten var olan merchant → save çağrılmaz (idempotent)")
        void learn_existingMerchant_doesNotSave() {
            when(repository.findAll()).thenReturn(List.of(migros));

            service.learn("Migros", "Market", false);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("'Bilinmeyen' description → save çağrılmaz")
        void learn_bilinmeyen_doesNotSave() {
            service.learn("Bilinmeyen", "Diğer", false);

            verify(repository, never()).save(any());
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("Boş description → save çağrılmaz")
        void learn_blank_doesNotSave() {
            service.learn("   ", "Diğer", false);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("255 karakter üzeri description → save çağrılmaz")
        void learn_tooLongDescription_doesNotSave() {
            String longDesc = "X".repeat(256);

            service.learn(longDesc, "Diğer", false);

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Normalize eşleme: 'BİM' varsa 'bim' tekrar kaydedilmez")
        void learn_normalizedDuplicate_doesNotSave() {
            MerchantCache bim = MerchantCache.builder()
                    .id(5L).pattern("BİM").category("Market").subscription(false).build();
            when(repository.findAll()).thenReturn(List.of(bim));

            service.learn("bim", "Market", false);

            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // count
    // =========================================================================

    @Test
    @DisplayName("count() repository'yi devreder")
    void count_delegatesToRepository() {
        when(repository.count()).thenReturn(42L);

        assertThat(service.count()).isEqualTo(42L);
    }
}
