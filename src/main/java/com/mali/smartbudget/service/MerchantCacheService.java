package com.mali.smartbudget.service;

import com.mali.smartbudget.model.MerchantCache;
import com.mali.smartbudget.repository.MerchantCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Merchant adı → kategori/abonelik eşlemesini önbellekler.
 *
 * <p>İki aşamalı arama:
 * <ol>
 *   <li>Tam eşleme (case-insensitive, Türkçe İ → i normalize edilir)</li>
 *   <li>Pattern eşleme: cache deseni description içinde veya description cache deseninde bulunuyor mu?</li>
 * </ol>
 *
 * <p>Öğrenme: LLM'den gelen yeni merchant'lar otomatik kaydedilir.
 * Böylece her tekrar eden merchant için OpenAI maliyeti sıfıra iner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantCacheService {

    /** Lookup sonucu; category ve abonelik bilgisi taşır. */
    public record CachedResult(String category, boolean isSubscription) {}

    private final MerchantCacheRepository repository;

    // ─────────────────────────────────────────────────────────────────────────
    // Arama
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Description'a göre cache'de arama yapar.
     *
     * @param description LLM'den veya PDF parser'dan gelen merchant adı
     * @return Eşleşme bulunursa {@link CachedResult}; bulunmazsa boş Optional
     */
    @Transactional
    public Optional<CachedResult> lookup(String description) {
        if (description == null || description.isBlank()) return Optional.empty();

        String descNorm = normalize(description.trim());
        List<MerchantCache> all = repository.findAll();

        // 1. Tam eşleme
        for (MerchantCache entry : all) {
            if (descNorm.equals(normalize(entry.getPattern()))) {
                return hit(entry);
            }
        }

        // 2. Pattern eşleme: cache deseni description'da veya description cache deseninde
        for (MerchantCache entry : all) {
            String patternNorm = normalize(entry.getPattern());
            if (descNorm.contains(patternNorm) || patternNorm.contains(descNorm)) {
                return hit(entry);
            }
        }

        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Öğrenme
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LLM'den gelen yeni bir merchant'ı cache'e kaydeder.
     * Zaten varsa sessizce atlar (idempotent).
     *
     * @param description merchant adı (255 karakter üzeri atlanır)
     * @param category    Türkçe granüler kategori etiketi
     * @param isSubscription abonelik mi?
     */
    @Transactional
    public void learn(String description, String category, boolean isSubscription) {
        String pattern = description.trim();
        if (pattern.isBlank() || pattern.length() > 255 || "Bilinmeyen".equals(pattern)) return;

        String normPattern = normalize(pattern);

        // Mevcut kayıtlarla normalize karşılaştırma (Türkçe İ desteği)
        boolean exists = repository.findAll().stream()
                .anyMatch(e -> normalize(e.getPattern()).equals(normPattern));
        if (exists) return;

        repository.save(MerchantCache.builder()
                .pattern(pattern)
                .category(category)
                .subscription(isSubscription)
                .build());
        log.info("[cache] Yeni merchant öğrenildi: '{}' → {} (abonelik: {})",
                pattern, category, isSubscription);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // İstatistik
    // ─────────────────────────────────────────────────────────────────────────

    /** Toplam cache kayıt sayısı. */
    public long count() {
        return repository.count();
    }

    /**
     * Read-only pattern check — hitCount yan etkisi yoktur.
     * Transaction Router tarafından güven skoru hesaplamak için kullanılır.
     *
     * @param description kontrol edilecek merchant adı
     * @return cache'de eşleşen bir kayıt varsa {@code true}
     */
    @Transactional(readOnly = true)
    public boolean isKnown(String description) {
        if (description == null || description.isBlank()) return false;
        String needle = normalize(description.trim());
        return repository.findAll().stream().anyMatch(entry -> {
            String pat = normalize(entry.getPattern());
            return needle.equals(pat) || needle.contains(pat) || pat.contains(needle);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı
    // ─────────────────────────────────────────────────────────────────────────

    private Optional<CachedResult> hit(MerchantCache entry) {
        entry.setHitCount(entry.getHitCount() + 1);
        repository.save(entry);
        log.debug("[cache] Hit: '{}' → {} | toplam hit: {}",
                entry.getPattern(), entry.getCategory(), entry.getHitCount());
        return Optional.of(new CachedResult(entry.getCategory(), entry.isSubscription()));
    }

    /** İ (U+0130) → i normalize + lowercase; Türkçe büyük İ'nin case-fold hatasını engeller. */
    private String normalize(String s) {
        return s.replace('İ', 'i').toLowerCase(Locale.ROOT);
    }
}
