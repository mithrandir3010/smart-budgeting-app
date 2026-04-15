package com.mali.smartbudget.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    private Statement statement;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    private String category;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false, columnDefinition = "boolean DEFAULT false")
    private boolean isSubscription;

    @Column(nullable = false, columnDefinition = "boolean DEFAULT false")
    private boolean isInstallment;

    private Integer currentInstallment;

    private Integer totalInstallments;
}
