package com.mali.smartbudget.service;

import com.mali.smartbudget.model.RateLimitBlock;
import com.mali.smartbudget.repository.RateLimitBlockRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final RateLimitBlockRepository blockRepository;

    @Value("${app.rate-limit.capacity:10}")
    private int capacity;

    @Value("${app.rate-limit.refill-minutes:5}")
    private int refillMinutes;

    @Value("${app.rate-limit.upload-capacity:3}")
    private int uploadCapacity;

    @Value("${app.rate-limit.upload-refill-hours:1}")
    private int uploadRefillHours;

    @Value("${app.rate-limit.upload-monthly-capacity:10}")
    private int uploadMonthlyCapacity;

    @Value("${app.rate-limit.register-capacity:3}")
    private int registerCapacity;

    @Value("${app.rate-limit.register-refill-hours:1}")
    private int registerRefillHours;

    @Value("${app.rate-limit.api-capacity:60}")
    private int apiCapacity;

    @Value("${app.rate-limit.api-refill-minutes:1}")
    private int apiRefillMinutes;

    private final Map<String, Bucket> buckets              = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets      = new ConcurrentHashMap<>();
    private final Map<String, Bucket> uploadBuckets        = new ConcurrentHashMap<>();
    private final Map<String, Bucket> uploadMonthlyBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets           = new ConcurrentHashMap<>();

    /** Auth endpoint'leri için IP bazlı limit — restart'ta kaybolmaz (DB blok). */
    @Transactional
    public ConsumptionProbe tryConsume(String ip) {
        String key = "auth:" + ip;
        ConsumptionProbe dbBlock = checkDbBlock(key);
        if (dbBlock != null) return dbBlock;

        ConsumptionProbe probe = resolveBucket(ip).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            persistBlock(key, Duration.ofMinutes(refillMinutes));
        }
        return probe;
    }

    /** Register endpoint'i için IP bazlı sıkı limit — her kayıt bir e-posta tetikler. */
    @Transactional
    public ConsumptionProbe tryConsumeRegister(String ip) {
        String key = "register:" + ip;
        ConsumptionProbe dbBlock = checkDbBlock(key);
        if (dbBlock != null) return dbBlock;

        ConsumptionProbe probe = resolveRegisterBucket(ip).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            persistBlock(key, Duration.ofHours(registerRefillHours));
        }
        return probe;
    }

    /** Upload endpoint'i için kullanıcı bazlı limit — saatlik ve aylık kontrol. */
    @Transactional
    public ConsumptionProbe tryConsumeUpload(String userId) {
        // Aylık limit kontrolü
        String monthlyKey = "upload-monthly:" + userId;
        ConsumptionProbe monthlyDbBlock = checkDbBlock(monthlyKey);
        if (monthlyDbBlock != null) return monthlyDbBlock;

        ConsumptionProbe monthlyProbe = resolveUploadMonthlyBucket(userId).tryConsumeAndReturnRemaining(1);
        if (!monthlyProbe.isConsumed()) {
            persistBlock(monthlyKey, Duration.ofDays(30));
            return monthlyProbe;
        }

        // Saatlik limit kontrolü
        String hourlyKey = "upload:" + userId;
        ConsumptionProbe hourlyDbBlock = checkDbBlock(hourlyKey);
        if (hourlyDbBlock != null) return hourlyDbBlock;

        ConsumptionProbe probe = resolveUploadBucket(userId).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            persistBlock(hourlyKey, Duration.ofHours(uploadRefillHours));
        }
        return probe;
    }

    /** Authenticated API endpoint'leri için IP bazlı genel limit — yalnızca in-memory (1dk pencere). */
    public ConsumptionProbe tryConsumeApi(String ip) {
        return resolveApiBucket(ip).tryConsumeAndReturnRemaining(1);
    }

    /** Süresi dolmuş blokları 5 dakikada bir temizler. */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void purgeExpiredBlocks() {
        blockRepository.deleteExpired(Instant.now());
    }

    @Transactional
    public void clearAll() {
        buckets.clear();
        registerBuckets.clear();
        uploadBuckets.clear();
        uploadMonthlyBuckets.clear();
        apiBuckets.clear();
        blockRepository.deleteAll();
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private ConsumptionProbe checkDbBlock(String key) {
        return blockRepository.findActiveBlock(key, Instant.now())
                .map(block -> {
                    long nanosToWait = Math.max(0,
                            (block.getBlockedUntil().toEpochMilli() - Instant.now().toEpochMilli()) * 1_000_000L);
                    return ConsumptionProbe.rejected(0, nanosToWait, nanosToWait);
                })
                .orElse(null);
    }

    private void persistBlock(String key, Duration refillPeriod) {
        blockRepository.save(RateLimitBlock.builder()
                .bucketKey(key)
                .blockedUntil(Instant.now().plus(refillPeriod))
                .build());
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(capacity)
                                .refillGreedy(capacity, Duration.ofMinutes(refillMinutes))
                                .build())
                        .build());
    }

    private Bucket resolveRegisterBucket(String key) {
        return registerBuckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(registerCapacity)
                                .refillGreedy(registerCapacity, Duration.ofHours(registerRefillHours))
                                .build())
                        .build());
    }

    private Bucket resolveUploadBucket(String userId) {
        return uploadBuckets.computeIfAbsent(userId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(uploadCapacity)
                                .refillGreedy(uploadCapacity, Duration.ofHours(uploadRefillHours))
                                .build())
                        .build());
    }

    private Bucket resolveUploadMonthlyBucket(String userId) {
        return uploadMonthlyBuckets.computeIfAbsent(userId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(uploadMonthlyCapacity)
                                .refillGreedy(uploadMonthlyCapacity, Duration.ofDays(30))
                                .build())
                        .build());
    }

    private Bucket resolveApiBucket(String ip) {
        return apiBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(apiCapacity)
                                .refillGreedy(apiCapacity, Duration.ofMinutes(apiRefillMinutes))
                                .build())
                        .build());
    }
}
