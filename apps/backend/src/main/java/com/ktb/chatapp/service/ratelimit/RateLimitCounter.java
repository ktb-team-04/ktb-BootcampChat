package com.ktb.chatapp.service.ratelimit;

public record RateLimitCounter(long count, long ttlSeconds) {
}
