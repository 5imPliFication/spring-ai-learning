package com.example.ai.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    @Test
    void aiRequestsAreLimitedPerUserPerMinute() {
        var service = new RateLimitService(3, 100, 5);

        for (int i = 0; i < 3; i++) {
            assertThat(service.tryConsumeAi("alice").allowed()).isTrue();
        }
        var denied = service.tryConsumeAi("alice");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 60L);

        assertThat(service.tryConsumeAi("bob").allowed()).as("other users are unaffected").isTrue();
    }

    @Test
    void dailyLimitAppliesEvenWhenMinuteLimitIsHigher() {
        var service = new RateLimitService(100, 2, 5);

        assertThat(service.tryConsumeAi("alice").allowed()).isTrue();
        assertThat(service.tryConsumeAi("alice").allowed()).isTrue();
        var denied = service.tryConsumeAi("alice");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isGreaterThan(60L);
    }

    @Test
    void authAttemptsAreLimitedPerIp() {
        var service = new RateLimitService(10, 100, 2);

        assertThat(service.tryConsumeAuth("1.1.1.1").allowed()).isTrue();
        assertThat(service.tryConsumeAuth("1.1.1.1").allowed()).isTrue();
        assertThat(service.tryConsumeAuth("1.1.1.1").allowed()).isFalse();
        assertThat(service.tryConsumeAuth("2.2.2.2").allowed()).isTrue();
    }

    @Test
    void evictionKeepsRecentlyUsedBuckets() {
        var service = new RateLimitService(1, 100, 1);
        service.tryConsumeAuth("1.1.1.1");
        service.tryConsumeAi("alice");

        service.evictIdleBuckets();

        assertThat(service.tryConsumeAuth("1.1.1.1").allowed()).as("still throttled after cleanup").isFalse();
        assertThat(service.tryConsumeAi("alice").allowed()).isFalse();
    }
}
