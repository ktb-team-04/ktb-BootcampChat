package com.ktb.chatapp.service;

import com.ktb.chatapp.service.ratelimit.RateLimitCounter;
import com.ktb.chatapp.service.ratelimit.RateLimitStore;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitStore rateLimitStore;

    public RateLimitCheckResult checkRateLimit(String clientId, int maxRequests, Duration window) {
        String actualClientId = String.valueOf(clientId);
        Duration effectiveWindow = window != null ? window : Duration.ofSeconds(1);
        long windowSeconds = Math.max(1L, effectiveWindow.getSeconds());
        long nowEpochSeconds = Instant.now().getEpochSecond();

        try {
            RateLimitCounter counter = rateLimitStore.incrementAndGet(actualClientId, windowSeconds);
            long ttlSeconds = Math.max(1L, counter.ttlSeconds());
            long resetEpochSeconds = nowEpochSeconds + ttlSeconds;
            if (counter.count() > maxRequests) {
                return RateLimitCheckResult.rejected(
                        maxRequests, windowSeconds, resetEpochSeconds, ttlSeconds);
            }

            int remaining = (int) Math.max(0L, maxRequests - counter.count());
            return RateLimitCheckResult.allowed(
                    maxRequests, remaining, windowSeconds, resetEpochSeconds, ttlSeconds);
        } catch (Exception e) {
            log.error("Rate limit check failed for client: {}", actualClientId, e);
            long resetEpochSeconds = nowEpochSeconds + windowSeconds;
            return RateLimitCheckResult.allowed(
                    maxRequests, maxRequests, windowSeconds, resetEpochSeconds, windowSeconds);
        }
    }
}
