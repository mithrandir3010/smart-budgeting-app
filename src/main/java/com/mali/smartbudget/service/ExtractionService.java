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
 * <h3>Hibrit MCP Mimarisindeki Yeri</h3>
 * <pre>
 *  PDF Yükleme  →  ExtractionService (LLM, sadece PDF için)  →  DB
 *  Dashboard    →  AnalyticsService  (kural tabanlı, LLM YOK) →  Frontend / MCP tools
 * </pre>
 * Dashboard'daki Serena mesajları ({@code coachAdvice}) bu servisi çağırmaz.
 * LLM yalnızca PDF metin → işlem JSON dönüşümünde kullanılır.
 *
 * <h3>Mock Modu</h3>
 * <ul>
 *   <li>{@code serena.extraction.mock=true} → Her zaman sahte veri, LLM çağrısı yok.</li>
 *   <li>Geçersiz/eksik {@code OPENAI_API_KEY} → Otomatik mock moda geçer.</li>
 * </ul>
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
              {"date": "2026-04-01", "description": "Migros Market", "amount": 245.90, "category": "Market", "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-03", "description": "Starbucks Kahve", "amount": 89.50, "category": "Kafe", "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-05", "description": "Kira Ödemesi", "amount": 12000.00, "category": "Kira", "currency": "TRY", "isSubscription": false},
              {"date": "2026-04-07", "description": "Netflix", "amount": 79.99, "category": "Eğlence", "currency": "TRY", "isSubscription": true},
              {"date": "2026-04-08", "description": "Spotify Premium", "amount": 49.99, "category": "Eğlence", "currency": "TRY", "isSubscription": true},
              {"date": "2026-04-10", "description": "iCloud Depolama 50GB", "amount": 14.99, "category": "Teknoloji", "currency": "TRY", "isSubscription": true}
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
                "currency": "TRY",
                "isSubscription": false
              }
            ]

            isSubscription kuralı:
            - Netflix, Spotify, YouTube Premium, Apple TV+, Disney+, Hulu gibi video/müzik akış servisleri → true
            - iCloud, Google One, Dropbox, OneDrive gibi bulut depolama abonelikleri → true
            - Amazon Prime, Trendyol Premium gibi e-ticaret üyelikleri → true
            - Gym, spor salonu, dergi, gazete dijital abonelikleri → true
            - Antivirüs, yazılım lisansları (Adobe, Microsoft 365 vb.) → true
            - Tek seferlik alışveriş, market, kafe, restoran, ulaşım, kira vb. → false

            Sadece JSON döndür, başka bir açıklama ekleme.

            Ekstre metni:
            %s
            """;

    /**
     * {@code true} ise API anahtarı geçerli olsa dahi LLM çağrılmaz; sahte veri döner.
     * CI/CD ortamları veya demo modları için yararlıdır.
     * Varsayılan: {@code false} (API key kontrolü isMockMode() tarafından yapılır).
     */
    @Value("${serena.extraction.mock:false}")
    private boolean forceMockMode;

    // @Value non-final — Lombok @RequiredArgsConstructor yalnızca final alanları işler
    @Value("${langchain4j.open-ai.chat-model.api-key:" + DEFAULT_API_KEY + "}")
    private String openAiApiKey;

    private final ChatLanguageModel chatLanguageModel;
    private final PdfService pdfService;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    /**
     * Mock modunun aktif olup olmadığını belirler.
     *
     * <p>Öncelik sırası:
     * <ol>
     *   <li>{@code serena.extraction.mock=true} → Her koşulda mock (explicit override)</li>
     *   <li>Eksik / geçersiz API anahtarı → Otomatik mock</li>
     *   <li>Her ikisi de yoksa → Gerçek LLM çağrısı</li>
     * </ol>
     */
    private boolean isMockMode() {
        if (forceMockMode) return true;                        // explicit config override
        if (openAiApiKey == null || openAiApiKey.isBlank()) return true;
        if (DEFAULT_API_KEY.equals(openAiApiKey)) return true;
        if (!openAiApiKey.startsWith("sk-")) return true;     // gerçek OpenAI key değil
        return false;
    }

    /**
     * PDF dosyasını okur; Mock Mode'da sahte JSON, aksi hâlde LLM yanıtı döner.
     *
     * <p>NOT: Bu metot yalnızca PDF yükleme akışında çağrılır.
     * Dashboard Serena kartı bu metodu hiçbir zaman çağırmaz.
     */
    public String extractTransactionsAsJson(MultipartFile file) throws IOException {
        boolean mock = isMockMode();
        log.info(">>> Extraction mode: {} | forceMockMode={} | apiKey başlangıcı={}",
                mock ? "MOCK" : "LLM", forceMockMode,
                openAiApiKey != null && openAiApiKey.length() > 6
                    ? openAiApiKey.substring(0, 6) + "…" : "(kısa/null)");

        if (mock) {
            log.warn("[1/4] MOCK MODE: Sahte harcamalar kullanılıyor.");
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

        // Adım 4 — Eski verileri temizle, ardından yenilerini kaydet
        log.info("[4/4] Kullanıcıya ait eski transaction'lar siliniyor. userId={}", userId);
        transactionService.deleteAllByUserId(userId);
        log.info("[4/4] Eski veriler silindi. Transaction entity'leri oluşturuluyor ve kaydediliyor...");
        List<Transaction> transactions = dtos.stream()
                .map(dto -> Transaction.builder()
                        .user(user)          // managed — aynı @Transactional içinde yüklendi
                        .date(dto.date())
                        .description(dto.description())
                        .amount(dto.amount())
                        .category(dto.category())
                        .currency(dto.currency())
                        .isSubscription(dto.isSubscription())
                        .build())
                .toList();

        // saveAllTransactions() REQUIRED propagation ile bu transaction'a katılır
        List<Transaction> saved = transactionService.saveAllTransactions(transactions);
        log.info("[4/4] {} adet Transaction başarıyla kaydedildi.", saved.size());

        return saved;
    }
}
