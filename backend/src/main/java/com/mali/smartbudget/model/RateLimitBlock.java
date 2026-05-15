package com.mali.smartbudget.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "rate_limit_blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitBlock {

    @Id
    @Column(name = "bucket_key", length = 100)
    private String bucketKey;

    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;
}
