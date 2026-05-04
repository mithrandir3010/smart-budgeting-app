package com.mali.smartbudget.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "merchant_cache",
    indexes = @Index(name = "idx_merchant_pattern", columnList = "pattern")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Merchant adı veya tanımlayıcı alt-string (max 255 karakter). */
    @Column(nullable = false, unique = true, length = 255)
    private String pattern;

    /** Türkçe granüler kategori etiketi — CategorizationService.mapLlmLabel ile uyumlu. */
    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "is_subscription", nullable = false)
    @Builder.Default
    private boolean subscription = false;

    /** Kaç kez bu cache girdisi kullanıldı — analitik için. */
    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    private int hitCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
