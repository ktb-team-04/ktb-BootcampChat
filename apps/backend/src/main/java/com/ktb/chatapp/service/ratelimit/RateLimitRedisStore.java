package com.ktb.chatapp.service.ratelimit;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis fixed-window rate limit counter. */
@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "redis", matchIfMissing = true)
public class RateLimitRedisStore implements RateLimitStore {

    private static final String INCREMENT_SCRIPT_TEXT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return tostring(count) .. ':' .. tostring(ttl)
            """;

    private static final DefaultRedisScript<String> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(INCREMENT_SCRIPT_TEXT, String.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RateLimitRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.redis.key-prefix:chat:rate-limit:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public RateLimitCounter incrementAndGet(String clientId, long windowSeconds) {
        String result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(keyPrefix + clientId),
                Long.toString(windowSeconds));
        if (result == null) {
            throw new IllegalStateException("Redis rate limit script returned no result");
        }

        int separator = result.indexOf(':');
        if (separator < 1) {
            throw new IllegalStateException("Invalid Redis rate limit result: " + result);
        }
        return new RateLimitCounter(
                Long.parseLong(result.substring(0, separator)),
                Long.parseLong(result.substring(separator + 1)));
    }
}
