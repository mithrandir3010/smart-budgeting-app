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

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public ConsumptionProbe tryConsume(String ip) {
        return resolveBucket(ip).tryConsumeAndReturnRemaining(1);
    }

    private Bucket resolveBucket(String ip) {
        return buckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(capacity)
                                .refillGreedy(capacity, Duration.ofMinutes(refillMinutes))
                                .build())
                        .build());
    }

    public void clearAll() {
        buckets.clear();
    }
}
