package com.mali.smartbudget.service;

import com.mali.smartbudget.model.MerchantCache;
import com.mali.smartbudget.repository.MerchantCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Uygulama başlangıcında merchant_cache tablosunu popüler Türk merchant'larıyla doldurur.
 * Sadece prod/dev profilde çalışır; test profili bu seeder'ı atlar.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class MerchantCacheSeeder implements ApplicationRunner {

    private final MerchantCacheRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("[cache-seeder] Merchant cache zaten dolu ({} kayıt) — seed atlandı.",
                    repository.count());
            return;
        }

        List<MerchantCache> seeds = buildSeeds();
        repository.saveAll(seeds);
        log.info("[cache-seeder] {} merchant başlangıç verisi eklendi.", seeds.size());
    }

    private List<MerchantCache> buildSeeds() {
        return List.of(
            // ── Market / Gıda ────────────────────────────────────────────────
            seed("Migros",          "Market",    false),
            seed("BİM",             "Market",    false),
            seed("A101",            "Market",    false),
            seed("ŞOK",             "Market",    false),
            seed("Carrefour",       "Market",    false),
            seed("Hakmar",          "Market",    false),

            // ── Kafe ─────────────────────────────────────────────────────────
            seed("Starbucks",       "Kafe",      false),
            seed("Kahve Dünyası",   "Kafe",      false),
            seed("Gloria Jeans",    "Kafe",      false),

            // ── Restoran ─────────────────────────────────────────────────────
            seed("McDonald's",      "Restoran",  false),
            seed("KFC",             "Restoran",  false),
            seed("Burger King",     "Restoran",  false),
            seed("Popeyes",         "Restoran",  false),
            seed("Subway",          "Restoran",  false),

            // ── Ulaşım ───────────────────────────────────────────────────────
            seed("Uber",            "Ulaşım",    false),
            seed("BiTaksi",         "Ulaşım",    false),
            seed("İETT",            "Ulaşım",    false),
            seed("Marmaray",        "Ulaşım",    false),

            // ── Akaryakıt ────────────────────────────────────────────────────
            seed("Shell",           "Akaryakıt", false),
            seed("Opet",            "Akaryakıt", false),
            seed("BP",              "Akaryakıt", false),
            seed("Total",           "Akaryakıt", false),

            // ── Fatura ───────────────────────────────────────────────────────
            seed("Turkcell",        "Fatura",    false),
            seed("Vodafone",        "Fatura",    false),
            seed("Türk Telekom",    "Fatura",    false),

            // ── Eğlence / Abonelik ───────────────────────────────────────────
            seed("Netflix",         "Eğlence",   true),
            seed("Spotify",         "Eğlence",   true),
            seed("YouTube",         "Eğlence",   true),
            seed("Disney+",         "Eğlence",   true),
            seed("Amazon Prime",    "Eğlence",   true),
            seed("Todtv.com.tr",    "Eğlence",   true),

            // ── Teknoloji ────────────────────────────────────────────────────
            seed("Apple",           "Teknoloji", false),
            seed("iCloud",          "Teknoloji", true),

            // ── Giyim ────────────────────────────────────────────────────────
            seed("Zara",            "Giyim",     false),
            seed("LC Waikiki",      "Giyim",     false),
            seed("DeFacto",         "Giyim",     false),

            // ── Eğitim ───────────────────────────────────────────────────────
            seed("Udemy",           "Eğitim",    true)
        );
    }

    private MerchantCache seed(String pattern, String category, boolean subscription) {
        return MerchantCache.builder()
                .pattern(pattern)
                .category(category)
                .subscription(subscription)
                .build();
    }
}
