package com.mali.smartbudget.service;

import com.mali.smartbudget.model.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * İşlem açıklaması ve LLM kategori string'ini üst-seviye {@link Category} enum değerine dönüştürür.
 *
 * <p>İki aşamalı strateji:
 * <ol>
 *   <li>LLM label eşleştirme — LLM'in ürettiği Türkçe kategori string'i doğrudan map edilir.</li>
 *   <li>Keyword fallback — LLM label bilinmiyorsa açıklama metni anahtar kelime taramasına tabi tutulur.</li>
 * </ol>
 */
@Slf4j
@Service
public class CategorizationService {

    /**
     * Verilen işlemi kategorize eder.
     *
     * @param description işlem açıklaması (POS adı, mağaza adı vb.)
     * @param llmCategory LLM'in atadığı Türkçe kategori string'i (nullable)
     * @return eşleşen {@link Category} enum değeri; hiçbiri eşleşmezse {@link Category#OTHER}
     */
    public Category categorize(String description, String llmCategory) {
        if (llmCategory != null && !llmCategory.isBlank()) {
            Category mapped = mapLlmLabel(llmCategory.trim());
            if (mapped != null) {
                log.debug("Transaction '{}' categorized as {} (LLM label: {})",
                        description, mapped, llmCategory);
                return mapped;
            }
        }

        Category keyword = matchByKeyword(description);
        log.debug("Transaction '{}' categorized as {} (keyword fallback)", description, keyword);
        return keyword;
    }

    // ── LLM Label Eşleştirme ────────────────────────────────────────────────

    private Category mapLlmLabel(String label) {
        return switch (label) {
            case "Market", "Kafe", "Restoran"           -> Category.FOOD;
            case "Ulaşım", "Akaryakıt"                  -> Category.TRANSPORT;
            case "Kira", "Fatura"                       -> Category.HOUSING;
            case "Giyim", "Teknoloji"                   -> Category.SHOPPING;
            case "Sağlık"                               -> Category.HEALTH;
            case "Eğitim"                               -> Category.EDUCATION;
            case "Eğlence", "Sigorta"                   -> Category.ENTERTAINMENT;
            case "Diğer"                                -> Category.OTHER;
            default                                     -> null;
        };
    }

    // ── Keyword Fallback ────────────────────────────────────────────────────

    private Category matchByKeyword(String description) {
        if (description == null || description.isBlank()) return Category.OTHER;
        // Locale.ROOT: Türkçe I/ı dönüşümünden bağımsız güvenli ASCII karşılaştırması
        String lower = description.toLowerCase(Locale.ROOT);

        if (lower.contains("migros") || lower.contains("bim") || lower.contains("a101")
                || lower.contains("şok") || lower.contains("market") || lower.contains("kafe")
                || lower.contains("coffee") || lower.contains("starbucks") || lower.contains("restoran")
                || lower.contains("burger") || lower.contains("pizza") || lower.contains("döner")
                || lower.contains("mcdonalds") || lower.contains("kfc") || lower.contains("manav")
                || lower.contains("kasap") || lower.contains("fırın")) {
            return Category.FOOD;
        }
        if (lower.contains("taksi") || lower.contains("uber") || lower.contains("iett")
                || lower.contains("metro") || lower.contains("otobüs") || lower.contains("shell")
                || lower.contains("opet") || lower.contains("bp") || lower.contains("petrol")
                || lower.contains("akaryakıt") || lower.contains("benzin") || lower.contains("marmaray")
                || lower.contains("metrobüs") || lower.contains("dolmuş")) {
            return Category.TRANSPORT;
        }
        if (lower.contains("kira") || lower.contains("aidat") || lower.contains("elektrik")
                || lower.contains("doğalgaz") || lower.contains("iski") || lower.contains("igdaş")
                || lower.contains("turkcell") || lower.contains("vodafone") || lower.contains("türk telekom")
                || lower.contains("fatura") || lower.contains("internet")) {
            return Category.HOUSING;
        }
        if (lower.contains("zara") || lower.contains("h&m") || lower.contains("lcwaikiki")
                || lower.contains("mavi") || lower.contains("koton") || lower.contains("defacto")
                || lower.contains("adidas") || lower.contains("nike") || lower.contains("samsung")
                || lower.contains("apple") || lower.contains("teknosa") || lower.contains("mediamarkt")
                || lower.contains("vatan") || lower.contains("trendyol")) {
            return Category.SHOPPING;
        }
        if (lower.contains("eczane") || lower.contains("hastane") || lower.contains("klinik")
                || lower.contains("doktor") || lower.contains("diş") || lower.contains("optik")
                || lower.contains("laborat") || lower.contains("pharmacy")) {
            return Category.HEALTH;
        }
        if (lower.contains("üniversite") || lower.contains("kurs") || lower.contains("udemy")
                || lower.contains("coursera") || lower.contains("kitap") || lower.contains("okul")
                || lower.contains("dershane")) {
            return Category.EDUCATION;
        }
        if (lower.contains("netflix") || lower.contains("spotify") || lower.contains("sinema")
                || lower.contains("youtube") || lower.contains("disney") || lower.contains("tiyatro")
                || lower.contains("konser") || lower.contains("amazon prime")) {
            return Category.ENTERTAINMENT;
        }
        return Category.OTHER;
    }
}
