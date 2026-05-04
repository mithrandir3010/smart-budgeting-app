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
            case "Market", "Kafe", "Restoran",
                 "Online Yemek"                         -> Category.FOOD;
            case "Ulaşım", "Akaryakıt",
                 "Araç Bakım"                           -> Category.TRANSPORT;
            case "Kira", "Fatura"                       -> Category.HOUSING;
            case "Giyim", "Teknoloji",
                 "Online Alışveriş", "Ev & Yaşam"       -> Category.SHOPPING;
            case "Sağlık", "Spor"                       -> Category.HEALTH;
            case "Eğitim"                               -> Category.EDUCATION;
            case "Eğlence", "Sigorta", "Seyahat"        -> Category.ENTERTAINMENT;
            case "Diğer"                                -> Category.OTHER;
            default                                     -> null;
        };
    }

    // ── Keyword Fallback ────────────────────────────────────────────────────

    private Category matchByKeyword(String description) {
        if (description == null || description.isBlank()) return Category.OTHER;
        String lower = description.replace('İ', 'i').toLowerCase(Locale.ROOT);

        if (lower.contains("migros") || lower.contains("bim") || lower.contains("a101")
                || lower.contains("şok") || lower.contains("carrefour") || lower.contains("hakmar")
                || lower.contains("macro") || lower.contains("kiler") || lower.contains("market")
                || lower.contains("yemeksepeti") || lower.contains("getir") || lower.contains("trendyol go")
                || lower.contains("starbucks") || lower.contains("gloria jean") || lower.contains("kahve")
                || lower.contains("coffee") || lower.contains("cafe") || lower.contains("kafe")
                || lower.contains("restoran") || lower.contains("lokanta") || lower.contains("burger")
                || lower.contains("pizza") || lower.contains("döner") || lower.contains("kebap")
                || lower.contains("mcdonald") || lower.contains("kfc") || lower.contains("popeyes")
                || lower.contains("manav") || lower.contains("kasap") || lower.contains("fırın")
                || lower.contains("pastane") || lower.contains("kuruyemiş") || lower.contains("gıda")
                || lower.contains("yemek") || lower.contains("büfe")) {
            return Category.FOOD;
        }
        if (lower.contains("taksi") || lower.contains("uber") || lower.contains("bitaksi")
                || lower.contains("iett") || lower.contains("otobüs") || lower.contains("marmaray")
                || lower.contains("metrobüs") || lower.contains("dolmuş") || lower.contains("vapur")
                || lower.contains("tcdd") || lower.contains("otoyol") || lower.contains("hgs")
                || lower.contains("ispark") || lower.contains("otopark") || lower.contains("park ")
                || lower.contains("pegasus") || lower.contains("thy ") || lower.contains("anadolujet")
                || lower.contains("shell") || lower.contains("opet") || lower.contains("total")
                || lower.contains("petrol") || lower.contains("akaryakıt") || lower.contains("benzin")
                || lower.contains("lastik") || lower.contains("oto servis") || lower.contains("oto yıkama")) {
            return Category.TRANSPORT;
        }
        if (lower.contains("kira") || lower.contains("aidat") || lower.contains("elektrik")
                || lower.contains("doğalgaz") || lower.contains("iski") || lower.contains("igdas")
                || lower.contains("igdaş") || lower.contains("ayedas") || lower.contains("bedas")
                || lower.contains("turkcell") || lower.contains("vodafone") || lower.contains("türk telekom")
                || lower.contains("superonline") || lower.contains("türknet") || lower.contains("fatura")
                || lower.contains("internet") || lower.contains("abonelik")) {
            return Category.HOUSING;
        }
        if (lower.contains("zara") || lower.contains("h&m") || lower.contains("lcwaikiki")
                || lower.contains("mavi") || lower.contains("koton") || lower.contains("defacto")
                || lower.contains("bershka") || lower.contains("adidas") || lower.contains("nike")
                || lower.contains("puma") || lower.contains("giyim") || lower.contains("ayakkabı")
                || lower.contains("samsung") || lower.contains("apple") || lower.contains("teknosa")
                || lower.contains("mediamarkt") || lower.contains("vatan") || lower.contains("arçelik")
                || lower.contains("trendyol") || lower.contains("hepsiburada") || lower.contains("amazon")
                || lower.contains("ikea") || lower.contains("koçtaş") || lower.contains("mobilya")) {
            return Category.SHOPPING;
        }
        if (lower.contains("eczane") || lower.contains("pharmacy") || lower.contains("hastane")
                || lower.contains("hospital") || lower.contains("klinik") || lower.contains("doktor")
                || lower.contains("diş") || lower.contains("dental") || lower.contains("optik")
                || lower.contains("laborat") || lower.contains("acıbadem") || lower.contains("medicana")
                || lower.contains("fitness") || lower.contains("gym") || lower.contains("spor salonu")
                || lower.contains("decathlon") || lower.contains("macfit") || lower.contains("pilates")) {
            return Category.HEALTH;
        }
        if (lower.contains("üniversite") || lower.contains("kurs") || lower.contains("udemy")
                || lower.contains("coursera") || lower.contains("okul") || lower.contains("dershane")
                || lower.contains("kırtasiye") || lower.contains("kitap") || lower.contains("d&r")) {
            return Category.EDUCATION;
        }
        if (lower.contains("netflix") || lower.contains("spotify") || lower.contains("youtube")
                || lower.contains("disney") || lower.contains("exxen") || lower.contains("blutv")
                || lower.contains("amazon prime") || lower.contains("sinema") || lower.contains("tiyatro")
                || lower.contains("konser") || lower.contains("steam") || lower.contains("playstation")
                || lower.contains("airbnb") || lower.contains("otel") || lower.contains("hotel")
                || lower.contains("tatil") || lower.contains("sigorta") || lower.contains("kasko")) {
            return Category.ENTERTAINMENT;
        }
        return Category.OTHER;
    }
}
