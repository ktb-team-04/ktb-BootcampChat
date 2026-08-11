package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.User;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthUserCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesUsersAndClearsTheNegativeCacheEntry() {
        AuthUserCache cache = new AuthUserCache(10, Duration.ofMinutes(1), 10, Duration.ofMinutes(1));
        String email = "load@example.com";
        User user = User.builder().id("user-1").email(email).password("hash").name("Load").build();

        cache.markMissing(email);
        assertThat(cache.isMissing(email)).isTrue();

        cache.put(user);

        assertThat(cache.get(email)).isSameAs(user);
        assertThat(cache.isMissing(email)).isFalse();
    }

    @Test
    void invalidatesPositiveAndNegativeEntries() {
        AuthUserCache cache = new AuthUserCache(10, Duration.ofMinutes(1), 10, Duration.ofMinutes(1));
        String email = "deleted@example.com";
        User user = User.builder().id("user-2").email(email).password("hash").name("Deleted").build();

        cache.put(user);
        cache.markMissing(email);
        cache.invalidate(email);

        assertThat(cache.get(email)).isNull();
        assertThat(cache.isMissing(email)).isFalse();
    }

    @Test
    void readsUserFromRedisAndWarmsLocalCache() throws Exception {
        String email = "redis@example.com";
        User user = User.builder().id("user-3").email(email).password("hash").name("Redis").build();
        AuthUserCache writer = redisBackedCache();
        writer.put(user);

        var jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("test:auth:user:" + email), jsonCaptor.capture(), eq(Duration.ofMinutes(1)));

        AuthUserCache reader = redisBackedCache();
        when(valueOperations.get("test:auth:user:" + email)).thenReturn(jsonCaptor.getValue());

        User cached = reader.get(email);

        assertThat(cached.getId()).isEqualTo("user-3");
        assertThat(cached.getPassword()).isEqualTo("hash");
        assertThat(reader.get(email)).isSameAs(cached);
    }

    @Test
    void storesAndReadsMissingMarkerFromRedis() {
        String email = "missing@example.com";
        AuthUserCache cache = redisBackedCache();

        cache.markMissing(email);

        verify(valueOperations).set("test:auth:missing:" + email, "1", Duration.ofMinutes(1));

        AuthUserCache reader = redisBackedCache();
        when(redisTemplate.hasKey("test:auth:missing:" + email)).thenReturn(true);

        assertThat(reader.isMissing(email)).isTrue();
        assertThat(reader.isMissing(email)).isTrue();
    }

    @Test
    void redisFailuresFallBackToLocalCacheMiss() {
        AuthUserCache cache = redisBackedCache();
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(cache.get("fail@example.com")).isNull();
        assertThat(cache.isMissing("fail@example.com")).isFalse();
    }

    @Test
    void redisCacheIsDisabledByDefault() {
        AuthUserCache cache = new AuthUserCache(
                redisTemplate,
                JsonMapper.builder().build(),
                10,
                Duration.ofMinutes(1),
                10,
                Duration.ofMinutes(1),
                false,
                "test:auth:user:",
                "test:auth:missing:");
        String email = "local-only@example.com";

        cache.put(User.builder().id("user-4").email(email).password("hash").build());
        cache.invalidate(email);
        assertThat(cache.get("unknown@example.com")).isNull();
        assertThat(cache.isMissing("unknown@example.com")).isFalse();

        verify(redisTemplate, never()).hasKey(anyString());
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    private AuthUserCache redisBackedCache() {
        return new AuthUserCache(
                redisTemplate,
                JsonMapper.builder().build(),
                10,
                Duration.ofMinutes(1),
                10,
                Duration.ofMinutes(1),
                true,
                "test:auth:user:",
                "test:auth:missing:");
    }
}
