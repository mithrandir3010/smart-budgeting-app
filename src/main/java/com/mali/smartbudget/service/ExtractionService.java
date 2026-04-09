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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * PDF ekstreden işlem (Transaction) verilerini LLM aracılığıyla ayıklayan ve kaydeden servis.
 *
 * <p>Akış: PDF → PdfService → LLM → JSON → TransactionDto[] → Transaction[] → DB
 *
 * <p>OPENAI_API_KEY ayarlanmamışsa Mock Mode devreye girer.
 *
 * <p>Tasarım notu: extractAndMap() hem ayıklamayı hem kaydetmeyi tek @Transactional
 * boundary içinde yapar. Böylece userRepository.findById() ile yüklenen User entity'si
 * saveAll() çağrısına kadar managed (yönetilen) kalır — detached entity hatası oluşmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private static final String DEFAULT_API_KEY = "your-api-key-here";

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

    // @Value non-final — Lombok @RequiredArgsConstructor yalnızca final alanları işler
    @Value("${langchain4j.open-ai.chat-model.api-key:" + DEFAULT_API_KEY + "}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    /**
     * Anahtar gerçek bir OpenAI key'i değilse Mock Mode aktif olur.
     * Geçersiz sayılan değerler: null, boş string, varsayılan placeholder,
     * "sk-" ile başlamayan her değer (gerçek OpenAI keyleri "sk-" ile başlar).
     */
    private boolean isMockMode() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) return true;
        if (DEFAULT_API_KEY.equals(openAiApiKey)) return true;
        if (!openAiApiKey.startsWith("sk-")) return true;
        return false;
    }

    /**
     * PDF dosyasını okur; Mock Mode'da sahte JSON, aksi hâlde LLM yanıtı döner.
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        log.info(">>> Gelen Key: '{}', Mock Mode Aktif mi: {}", openAiApiKey, isMockMode());

        if (isMockMode()) {
            log.warn("[1/4] MOCK MODE: Sahte harcamalar üretiliyor — OpenAI key geçerli değil.");
            return MOCK_JSON;
        }

        String rawText = pdfService.extractText(file);
        log.info("[1/4] PDF okundu. Karakter sayısı: {}", rawText.length());

        String prompt = String.format(PROMPT_TEMPLATE, rawText);
        log.info("[2/4] LLM isteği gönderiliyor...");

        String jsonResponse = chatLanguageModel.generate(prompt);
        log.info("[2/4] LLM yanıtı alındı. Yanıt uzunluğu: {} karakter", jsonResponse.length());

        return jsonResponse;
    }

    /**
     * PDF'ten harcamaları ayıklar, veritabanına kaydeder ve kaydedilen listeyi döner.
     *
     * <p>Tüm adımlar tek @Transactional boundary içinde çalışır:
     * User entity yüklendiği andan saveAll() bitene kadar managed kalır.
     *
     * @param file   Kullanıcının yüklediği PDF ekstre dosyası
     * @param userId Sahip kullanıcının ID'si
     * @return Veritabanına kaydedilmiş Transaction listesi
     */
    @Transactional
    public List<Transaction> extractAndMap(MultipartFile file, Long userId) throws IOException {

        // Adım 1 — Kullanıcıyı yükle (bu session boyunca managed kalacak)
        log.info("[1/4] Kullanıcı yükleniyor. userId={}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));
        log.info("[1/4] Kullanıcı bulundu: {}", user.getEmail());

        // Adım 2 — JSON al (Mock ya da LLM)
        log.info("[2/4] JSON ayıklama başlıyor...");
        String json = extractTransactionsAsJson(file);

        // LLM bazen ```json ... ``` bloğu içinde döner — temizle
        String cleanJson = json.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("```$", "")
                .trim();
        log.info("[2/4] JSON temizlendi. İlk 100 karakter: {}",
                cleanJson.substring(0, Math.min(100, cleanJson.length())));

        // Adım 3 — JSON → TransactionDto[]
        log.info("[3/4] JSON parse ediliyor...");
        List<TransactionDto> dtos;
        try {
            dtos = objectMapper.readValue(cleanJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("[3/4] JSON parse hatası. Gelen metin:\n{}", cleanJson);
            throw new IllegalArgumentException(
                    "LLM geçerli bir JSON döndürmedi. Detay: " + e.getOriginalMessage(), e);
        }
        log.info("[3/4] Parse tamamlandı. {} DTO oluşturuldu.", dtos.size());

        // Adım 4 — TransactionDto → Transaction entity (managed User ile aynı transaction'da)
        log.info("[4/4] Transaction entity'leri oluşturuluyor ve kaydediliyor...");
        List<Transaction> transactions = dtos.stream()
                .map(dto -> Transaction.builder()
                        .user(user)          // managed — aynı @Transactional içinde yüklendi
                        .date(dto.date())
                        .description(dto.description())
                        .amount(dto.amount())
                        .category(dto.category())
                        .currency(dto.currency())
                        .build())
                .toList();

        // saveAllTransactions() REQUIRED propagation ile bu transaction'a katılır
        List<Transaction> saved = transactionService.saveAllTransactions(transactions);
        log.info("[4/4] {} adet Transaction başarıyla kaydedildi.", saved.size());

        return saved;
    }
}
