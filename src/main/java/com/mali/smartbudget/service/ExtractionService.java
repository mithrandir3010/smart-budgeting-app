package com.mali.smartbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.UserRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * PDF ekstreden işlem (Transaction) verilerini LLM aracılığıyla ayıklayan servis.
 *
 * <p>Akış: PDF → PdfService (ham metin) → LLM prompt → JSON → TransactionDto[] → Transaction[]
 * <p>OPENAI_API_KEY ayarlanmamışsa otomatik olarak Mock Mode'a geçer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private static final String DEFAULT_API_KEY = "your-api-key-here";

    // 3 gerçekçi örnek harcama — Mock Mode'da döndürülür
    private static final String MOCK_JSON = """
            [
              {"date": "2026-04-01", "description": "Migros Market", "amount": 245.90, "category": "Market", "currency": "TRY"},
              {"date": "2026-04-03", "description": "Starbucks Kahve", "amount": 89.50, "category": "Kafe", "currency": "TRY"},
              {"date": "2026-04-05", "description": "Kira Ödemesi", "amount": 12000.00, "category": "Kira", "currency": "TRY"}
            ]
            """;

    private static final String PROMPT_TEMPLATE = """
            Aşağıdaki ekstre metnindeki harcamaları tarih, açıklama ve tutar olarak ayıkla \
            ve JSON formatında dön.

            JSON yapısı tam olarak şu şekilde olmalıdır:
            [
              {
                "date": "YYYY-MM-DD",
                "description": "işlem açıklaması",
                "amount": 123.45,
                "category": "kategori (ör: Market, Fatura, Ulaşım)",
                "currency": "TRY"
              }
            ]

            Sadece JSON döndür, başka bir açıklama ekleme.

            Ekstre metni:
            %s
            """;

    // @Value non-final olmalı — Lombok @RequiredArgsConstructor yalnızca final alanları kapsar
    @Value("${langchain4j.open-ai.chat-model.api-key:" + DEFAULT_API_KEY + "}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private boolean isMockMode() {
        return DEFAULT_API_KEY.equals(openAiApiKey);
    }

    /**
     * PDF dosyasını okur; Mock Mode'da sahte JSON, aksi hâlde LLM yanıtı döner.
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        if (isMockMode()) {
            log.warn("MOCK MODE: Sahte harcamalar üretiliyor — gerçek OPENAI_API_KEY ayarlanmamış.");
            return MOCK_JSON;
        }

        String rawText = pdfService.extractText(file);
        log.info("PDF okundu. Karakter sayısı: {}", rawText.length());

        String prompt = String.format(PROMPT_TEMPLATE, rawText);
        log.info("LLM isteği gönderiliyor...");

        String jsonResponse = chatLanguageModel.generate(prompt);
        log.info("LLM yanıtı alındı. Yanıt uzunluğu: {} karakter", jsonResponse.length());

        return jsonResponse;
    }

    /**
     * LLM yanıtını parse ederek kullanıcıya bağlı Transaction listesine dönüştürür.
     * JSON parse hatası oluşursa anlamlı bir IllegalArgumentException fırlatır.
     *
     * @param file   Kullanıcının yüklediği PDF ekstre dosyası
     * @param userId Sahip kullanıcının ID'si
     * @return Kaydedilmeye hazır Transaction listesi
     */
    public List<Transaction> extractAndMap(MultipartFile file, Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        String json = extractTransactionsAsJson(file);

        // LLM bazen yanıtı ```json ... ``` bloğu içinde döner — temizle
        String cleanJson = json.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("```$", "")
                .trim();

        log.debug("Parse edilecek JSON:\n{}", cleanJson);

        List<TransactionDto> dtos;
        try {
            dtos = objectMapper.readValue(cleanJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("LLM yanıtı geçerli bir JSON değil:\n{}", cleanJson);
            throw new IllegalArgumentException(
                    "LLM geçerli bir JSON döndürmedi. Lütfen tekrar deneyin. Detay: " + e.getOriginalMessage(), e);
        }

        List<Transaction> transactions = dtos.stream()
                .map(dto -> Transaction.builder()
                        .user(user)
                        .date(dto.date())
                        .description(dto.description())
                        .amount(dto.amount())
                        .category(dto.category())
                        .currency(dto.currency())
                        .build())
                .toList();

        log.info("{} adet Transaction oluşturuldu.", transactions.size());
        return transactions;
    }
}
