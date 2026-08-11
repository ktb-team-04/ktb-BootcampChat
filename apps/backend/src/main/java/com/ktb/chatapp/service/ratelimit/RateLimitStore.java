package com.ktb.chatapp.service.ratelimit;

/**
 * Data store interface for rate limit storage.
 */
public interface RateLimitStore {

    /** Increments a client's fixed-window counter and returns its current TTL. */
    RateLimitCounter incrementAndGet(String clientId, long windowSeconds);
}
