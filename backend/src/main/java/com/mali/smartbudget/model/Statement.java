package com.mali.smartbudget.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Yüklenen her PDF ekstresinin metadata'sını tutar.
 *
 * <h3>Mükerrer Kayıt Engelleme Alanları</h3>
 * <ul>
 *   <li>{@code sha256Checksum} — PDF raw byte'larının SHA-256 özeti.
 *       Aynı dosyanın tekrar yüklenmesini önler. (user_id, checksum) ikilisi benzersizdir.</li>
 *   <li>{@code periodStart} / {@code periodEnd} — Ekstredeki en eski ve en yeni işlem tarihleri.
 *       Aynı dönemi kapsayan farklı bir dosyanın yüklenmesini önler.</li>
 * </ul>
 *
 * <p>Bu alanlar nullable'dır — mevcut Statement kayıtları ile geriye dönük uyumluluk sağlanır.
 * Yalnızca PROCESSED durumdaki kayıtlar çakışma kontrolüne dahil edilir.
 */
@Entity
@Table(name = "statements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private LocalDate uploadDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatementStatus status;

    // ── Mükerrer kayıt engelleme alanları ────────────────────────────────────

    /**
     * SHA-256 hex hash (64 karakter).
     * Nullable: mevcut Statement kayıtları bu alan olmadan oluşturulmuştu.
     * Yeni yüklemelerde her zaman dolu gelir.
     */
    @Column(name = "sha256_checksum", length = 64)
    private String sha256Checksum;

    /**
     * Ekstredeki en eski işlem tarihi.
     * Nullable: boş ekstre veya eski kayıtlar için null olabilir.
     */
    private LocalDate periodStart;

    /**
     * Ekstredeki en yeni işlem tarihi.
     * Nullable: boş ekstre veya eski kayıtlar için null olabilir.
     */
    private LocalDate periodEnd;
}
