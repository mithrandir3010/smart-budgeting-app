package com.mali.smartbudget.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Category;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmCategorizationService — Birim Testleri")
class LlmCategorizationServiceTest {

    @Mock ChatLanguageModel chatLanguageModel;
    @Mock MerchantCacheService merchantCacheService;

    private LlmCategorizationService service;
    private CategorizationService categorizationService;

    @BeforeEach
    void setUp() {
        categorizationService = new CategorizationService();
        service = new LlmCategorizationService(
                chatLanguageModel, merchantCacheService, categorizationService, new ObjectMapper());
    }

    private TransactionDto dto(String description, Category cat) {
        return new TransactionDto(LocalDate.now(), description, BigDecimal.TEN,
                "Diğer", "TRY", false, false, null, null, cat);
    }

    @Nested
    @DisplayName("LLM çözümleme")
    class Resolving {

        @Test
        @DisplayName("OTHER merchant LLM ile çözülür ve kategori güncellenir")
        void resolvesOtherMerchant() {
            when(chatLanguageModel.generate(anyString()))
                    .thenReturn("{\"Kemal Usta Lokantası\":\"Restoran\"}");

            List<TransactionDto> result = service.enrichOtherTransactions(
                    List.of(dto("Kemal Usta Lokantası", Category.OTHER)));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).categoryEnum()).isEqualTo(Category.FOOD);
            assertThat(result.get(0).category()).isEqualTo("Restoran");
        }

        @Test
        @DisplayName("Çözülen merchant cache'e yazılır")
        void learnedMerchantSavedToCache() {
            when(chatLanguageModel.generate(anyString()))
                    .thenReturn("{\"Kemal Usta Lokantası\":\"Restoran\"}");

            service.enrichOtherTransactions(List.of(dto("Kemal Usta Lokantası", Category.OTHER)));

            verify(merchantCacheService).learn("Kemal Usta Lokantası", "Restoran", false);
        }

        @Test
        @DisplayName("LLM 'Diğer' döndürürse cache'e yazılmaz")
        void digerNotSavedToCache() {
            when(chatLanguageModel.generate(anyString()))
                    .thenReturn("{\"XYZ Bilinmeyen\":\"Diğer\"}");

            service.enrichOtherTransactions(List.of(dto("XYZ Bilinmeyen", Category.OTHER)));

            verify(merchantCacheService, never()).learn(anyString(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Zaten kategorize edilmiş işlemler LLM'e gönderilmez")
        void nonOtherTransactionsSkipped() {
            List<TransactionDto> input = List.of(
                    dto("Migros", Category.FOOD),
                    dto("Netflix", Category.ENTERTAINMENT));

            List<TransactionDto> result = service.enrichOtherTransactions(input);

            verifyNoInteractions(chatLanguageModel);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("LLM hatası durumunda orijinal liste değişmeden döner")
        void llmErrorReturnOriginalList() {
            when(chatLanguageModel.generate(anyString())).thenThrow(new RuntimeException("timeout"));

            List<TransactionDto> input = List.of(dto("Bilinmeyen", Category.OTHER));
            List<TransactionDto> result = service.enrichOtherTransactions(input);

            assertThat(result.get(0).categoryEnum()).isEqualTo(Category.OTHER);
        }

        @Test
        @DisplayName("Markdown fences temizlenir")
        void markdownFencesStripped() {
            when(chatLanguageModel.generate(anyString()))
                    .thenReturn("```json\n{\"Shell Benzin\":\"Akaryakıt\"}\n```");

            List<TransactionDto> result = service.enrichOtherTransactions(
                    List.of(dto("Shell Benzin", Category.OTHER)));

            assertThat(result.get(0).categoryEnum()).isEqualTo(Category.TRANSPORT);
        }
    }
}
