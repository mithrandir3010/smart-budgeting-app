package com.mali.smartbudget.model;

/**
 * Harcama kategorileri için üst-seviye enum.
 *
 * <p>LLM'in ürettiği Türkçe kategori string'leri ({@code "Market"}, {@code "Kafe"}, vb.)
 * {@code CategorizationService} aracılığıyla bu enum değerlerine dönüştürülür.
 * Bu sayede frontend ikonları ve cross-language raporlama mümkün olur.
 */
public enum Category {

    /** Market, Kafe, Restoran */
    FOOD,

    /** Ulaşım, Akaryakıt */
    TRANSPORT,

    /** Kira, Fatura */
    HOUSING,

    /** Giyim, Teknoloji */
    SHOPPING,

    /** Sağlık */
    HEALTH,

    /** Eğitim */
    EDUCATION,

    /** Eğlence, Sigorta */
    ENTERTAINMENT,

    /** Diğer ve eşleşmeyen kategoriler */
    OTHER
}
