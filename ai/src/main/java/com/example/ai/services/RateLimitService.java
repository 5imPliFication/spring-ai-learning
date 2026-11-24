package com.example.ai.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory token-bucket rate limits.
 * <p>
 * Buckets live in this JVM only: they reset on redeploy and are not shared between replicas.
 * That is fine for a single instance; move to a distributed Bucket4j backend (e.g. Redis) before scaling out.
 */
@Service
public class RateLimitService {

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Entry {
        final Bucket bucket;
        volatile long lastUsedNanos = System.nanoTime();

        Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }

    // An idle bucket is guaranteed full again after its longest refill period, so it can be dropped
    // and recreated on demand without changing behaviour.
    private static final Duration AI_IDLE_EVICTION = Duration.ofDays(1);
    private static final Duration AUTH_IDLE_EVICTION = Duration.ofMinutes(1);

    private final Map<String, Entry> aiBuckets = new ConcurrentHashMap<>();
    private final Map<String, Entry> authBuckets = new ConcurrentHashMap<>();

    private final Supplier<Bucket> aiBucketFactory;
    private final Supplier<Bucket> authBucketFactory;

    public RateLimitService(@Value("${app.ratelimit.ai-per-minute:10}") long aiPerMinute,
                            @Value("${app.ratelimit.ai-per-day:100}") long aiPerDay,
                            @Value("${app.ratelimit.auth-per-minute:5}") long authPerMinute) {
        this.aiBucketFactory = () -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(aiPerMinute).refillGreedy(aiPerMinute, Duration.ofMinutes(1)).build())
                .addLimit(Bandwidth.builder().capacity(aiPerDay).refillGreedy(aiPerDay, Duration.ofDays(1)).build())
                .build();
        this.authBucketFactory = () -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(authPerMinute).refillGreedy(authPerMinute, Duration.ofMinutes(1)).build())
                .build();
    }

    /** One AI request by this user. */
    public Decision tryConsumeAi(String userId) {
        return consume(aiBuckets, userId, aiBucketFactory);
    }

    /** One login/register attempt from this client IP. */
    public Decision tryConsumeAuth(String clientIp) {
        return consume(authBuckets, clientIp, authBucketFactory);
    }

    private Decision consume(Map<String, Entry> buckets, String key, Supplier<Bucket> factory) {
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(factory.get()));
        entry.lastUsedNanos = System.nanoTime();
        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Decision(true, 0);
        }
        long seconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        return new Decision(false, seconds);
    }

    /** Keeps the maps from growing with every user or IP ever seen. */
    @Scheduled(fixedDelayString = "${app.ratelimit.cleanup-interval-ms:600000}")
    public void evictIdleBuckets() {
        long now = System.nanoTime();
        authBuckets.values().removeIf(e -> now - e.lastUsedNanos > AUTH_IDLE_EVICTION.toNanos());
        aiBuckets.values().removeIf(e -> now - e.lastUsedNanos > AI_IDLE_EVICTION.toNanos());
    }
}
