package com.mali.smartbudget.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kullanıcının kategori bazlı bütçe limiti.
 * Bir kullanıcı her kategori için en fazla bir limit tanımlayabilir (UNIQUE kısıtı).
 */
@Entity
@Table(
    name = "budget_limits",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal limitAmount;
}
