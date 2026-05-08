package com.mali.smartbudget.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    @Value("${app.rate-limit.capacity:10}")
    private int capacity;

    @Value("${app.rate-limit.refill-minutes:5}")
    private int refillMinutes;

    @Value("${app.rate-limit.upload-capacity:5}")
    private int uploadCapacity;

    @Value("${app.rate-limit.upload-refill-hours:1}")
    private int uploadRefillHours;

    private final Map<String, Bucket> buckets       = new ConcurrentHashMap<>();
    private final Map<String, Bucket> uploadBuckets = new ConcurrentHashMap<>();

    /** Auth endpoint'leri için IP bazlı limit. */
    public ConsumptionProbe tryConsume(String ip) {
        return resolveBucket(ip).tryConsumeAndReturnRemaining(1);
    }

    /** Upload endpoint'i için kullanıcı bazlı limit. */
    public ConsumptionProbe tryConsumeUpload(String userId) {
        return resolveUploadBucket(userId).tryConsumeAndReturnRemaining(1);
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

    private Bucket resolveUploadBucket(String userId) {
        return uploadBuckets.computeIfAbsent(userId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(uploadCapacity)
                                .refillGreedy(uploadCapacity, Duration.ofHours(uploadRefillHours))
                                .build())
                        .build());
    }

    public void clearAll() {
        buckets.clear();
        uploadBuckets.clear();
    }
}
