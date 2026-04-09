package com.mali.smartbudget.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * PDF ekstreden işlem (Transaction) verilerini LLM aracılığıyla ayıklayan servis.
 *
 * <p>Akış: PDF → PdfService (ham metin) → LLM prompt → JSON → TransactionDto[] → Transaction[]
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

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

    /**
     * PDF dosyasını okur, LLM'e gönderir ve ham JSON yanıtını döner.
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        String rawText = pdfService.extractText(file);
        log.info("PDF okundu. Karakter sayısı: {}", rawText.length());

        String prompt = String.format(PROMPT_TEMPLATE, rawText);
        log.info("LLM isteği gönderiliyor...");

        String jsonResponse = chatLanguageModel.generate(prompt);
        log.info("LLM yanıtı alındı. Yanıt uzunluğu: {} karakter", jsonResponse.length());

        return jsonResponse;
    }

    /**
     * LLM'den gelen JSON yanıtını parse ederek kullanıcıya bağlı Transaction listesine dönüştürür.
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

        List<TransactionDto> dtos = objectMapper.readValue(cleanJson, new TypeReference<>() {});

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
