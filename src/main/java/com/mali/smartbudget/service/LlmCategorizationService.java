package com.mali.smartbudget.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Category;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Diğer" kalan merchant'ları toplu LLM çağrısıyla kategorize eder.
 *
 * <p>Pipeline'daki rolü: kural motoru + merchant cache'de çözülemeyen işlemler
 * için son çare. Sonuçlar merchant cache'e yazılır; aynı merchant ikinci kez
 * bu servise gelmez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCategorizationService {

    private static final String CATEGORIZE_PROMPT = """
            Categorize each Turkish merchant. Return ONLY a JSON object: {"MerchantName":"Category"}.
            Valid categories (use exactly): Market, Kafe, Restoran, Online Yemek, Ulaşım, Akaryakıt, \
            Araç Bakım, Kira, Fatura, Giyim, Teknoloji, Online Alışveriş, Ev & Yaşam, Sağlık, Spor, \
            Eğitim, Eğlence, Sigorta, Seyahat, Diğer
            No explanation. No markdown. No extra keys.

            Merchants (JSON array): %s
            """;

    private static final int BATCH_SIZE = 20;

    private final ChatLanguageModel chatLanguageModel;
    private final MerchantCacheService merchantCacheService;
    private final CategorizationService categorizationService;
    private final ObjectMapper objectMapper;

    /**
     * Listede {@link Category#OTHER} olan işlemleri LLM'e gönderir, kategori atar ve
     * öğrenilen bilgiyi merchant cache'e yazar.
     *
     * @param dtos extraction pipeline'ından gelen DTO listesi
     * @return aynı liste; OTHER'lar çözüldüyse güncellenmiş kopyalarıyla
     */
    public List<TransactionDto> enrichOtherTransactions(List<TransactionDto> dtos) {
        List<String> unknowns = dtos.stream()
                .filter(d -> d.categoryEnum() == Category.OTHER)
                .map(TransactionDto::description)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        if (unknowns.isEmpty()) return dtos;

        log.info("[llm-cat] {} bilinmeyen merchant LLM'e gönderiliyor...", unknowns.size());
        StopWatch sw = new StopWatch();
        sw.start();

        Map<String, String> resolved = new HashMap<>();
        for (int i = 0; i < unknowns.size(); i += BATCH_SIZE) {
            List<String> batch = unknowns.subList(i, Math.min(i + BATCH_SIZE, unknowns.size()));
            resolved.putAll(callLlmForBatch(batch));
        }

        sw.stop();
        log.info("[llm-cat] {} merchant çözüldü | {}ms", resolved.size(), sw.getTotalTimeMillis());

        resolved.forEach((merchant, category) -> {
            if (!"Diğer".equals(category)) {
                merchantCacheService.learn(merchant, category, false);
            }
        });

        return applyResolved(dtos, resolved);
    }

    private Map<String, String> callLlmForBatch(List<String> merchants) {
        try {
            String merchantsJson = objectMapper.writeValueAsString(merchants);
            String prompt = CATEGORIZE_PROMPT.formatted(merchantsJson);
            String raw = chatLanguageModel.generate(prompt);
            log.debug("[llm-cat] Ham yanıt: {}", raw);
            String cleaned = stripMarkdownFences(raw.trim());
            Map<String, String> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
            log.debug("[llm-cat] Batch sonucu: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("[llm-cat] Batch LLM hatası — atlanıyor: {}", e.getMessage());
            return Map.of();
        }
    }

    private List<TransactionDto> applyResolved(List<TransactionDto> dtos, Map<String, String> resolved) {
        List<TransactionDto> result = new ArrayList<>(dtos.size());
        for (TransactionDto dto : dtos) {
            if (dto.categoryEnum() != Category.OTHER) {
                result.add(dto);
                continue;
            }
            String llmCategory = resolved.get(dto.description());
            if (llmCategory == null) {
                result.add(dto);
                continue;
            }
            Category enumCat = categorizationService.categorize(dto.description(), llmCategory);
            if (enumCat == Category.OTHER) {
                result.add(dto);
                continue;
            }
            result.add(new TransactionDto(
                    dto.date(), dto.description(), dto.amount(),
                    llmCategory, dto.currency(), dto.isSubscription(),
                    dto.isInstallment(), dto.currentInstallment(), dto.totalInstallments(),
                    enumCat
            ));
        }
        return result;
    }

    private String stripMarkdownFences(String s) {
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return s;
    }
}
