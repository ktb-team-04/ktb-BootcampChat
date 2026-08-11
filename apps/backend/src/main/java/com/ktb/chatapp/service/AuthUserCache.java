package com.ktb.chatapp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb.chatapp.model.User;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Bounded local caches for the single-instance authentication hot path. */
@Slf4j
@Component
public class AuthUserCache {

    private final Cache<String, User> users;
    private final Cache<String, Boolean> missingUsers;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration userTtl;
    private final Duration missingTtl;
    private final String redisUserKeyPrefix;
    private final String redisMissingKeyPrefix;

    @Autowired
    public AuthUserCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.auth.user-cache.maximum-size:20000}") long userMaximumSize,
            @Value("${app.auth.user-cache.ttl:15m}") Duration userTtl,
            @Value("${app.auth.negative-cache.maximum-size:5000}") long missingMaximumSize,
            @Value("${app.auth.negative-cache.ttl:1m}") Duration missingTtl,
            @Value("${app.auth.redis.user-key-prefix:chat:auth:user:}") String redisUserKeyPrefix,
            @Value("${app.auth.redis.missing-key-prefix:chat:auth:missing:}") String redisMissingKeyPrefix) {
        users = Caffeine.newBuilder()
                .maximumSize(userMaximumSize)
                .expireAfterWrite(userTtl)
                .recordStats()
                .build();
        missingUsers = Caffeine.newBuilder()
                .maximumSize(missingMaximumSize)
                .expireAfterWrite(missingTtl)
                .recordStats()
                .build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userTtl = userTtl;
        this.missingTtl = missingTtl;
        this.redisUserKeyPrefix = redisUserKeyPrefix;
        this.redisMissingKeyPrefix = redisMissingKeyPrefix;
    }

    AuthUserCache(
            long userMaximumSize,
            Duration userTtl,
            long missingMaximumSize,
            Duration missingTtl) {
        this(null, null, userMaximumSize, userTtl, missingMaximumSize, missingTtl,
                "chat:auth:user:", "chat:auth:missing:");
    }

    public User get(String email) {
        User user = users.getIfPresent(email);
        if (user != null) {
            return user;
        }

        CachedAuthUser cached = readRedisUser(email);
        if (cached == null) {
            return null;
        }

        user = cached.toUser();
        users.put(email, user);
        missingUsers.invalidate(email);
        return user;
    }

    public boolean isMissing(String email) {
        if (missingUsers.getIfPresent(email) != null) {
            return true;
        }
        if (redisTemplate == null) {
            return false;
        }
        try {
            boolean missing = Boolean.TRUE.equals(redisTemplate.hasKey(redisMissingKey(email)));
            if (missing) {
                missingUsers.put(email, Boolean.TRUE);
            }
            return missing;
        } catch (RuntimeException e) {
            log.warn("인증 negative 캐시 조회 실패 - email: {}", email, e);
            return false;
        }
    }

    public void put(User user) {
        users.put(user.getEmail(), user);
        missingUsers.invalidate(user.getEmail());
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisUserKey(user.getEmail()),
                    objectMapper.writeValueAsString(CachedAuthUser.from(user)),
                    userTtl);
            redisTemplate.delete(redisMissingKey(user.getEmail()));
        } catch (Exception e) {
            log.warn("인증 사용자 Redis 캐시 저장 실패 - email: {}", user.getEmail(), e);
        }
    }

    public void markMissing(String email) {
        missingUsers.put(email, Boolean.TRUE);
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(redisMissingKey(email), "1", missingTtl);
            redisTemplate.delete(redisUserKey(email));
        } catch (RuntimeException e) {
            log.warn("인증 negative Redis 캐시 저장 실패 - email: {}", email, e);
        }
    }

    public void invalidate(String email) {
        users.invalidate(email);
        missingUsers.invalidate(email);
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(redisUserKey(email));
            redisTemplate.delete(redisMissingKey(email));
        } catch (RuntimeException e) {
            log.warn("인증 Redis 캐시 무효화 실패 - email: {}", email, e);
        }
    }

    private CachedAuthUser readRedisUser(String email) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String value = redisTemplate.opsForValue().get(redisUserKey(email));
            return value == null ? null : objectMapper.readValue(value, CachedAuthUser.class);
        } catch (Exception e) {
            log.warn("인증 사용자 Redis 캐시 조회 실패 - email: {}", email, e);
            return null;
        }
    }

    private String redisUserKey(String email) {
        return redisUserKeyPrefix + email;
    }

    private String redisMissingKey(String email) {
        return redisMissingKeyPrefix + email;
    }

    private record CachedAuthUser(
            String id,
            String email,
            String name,
            String password,
            String profileImage) {

        private static CachedAuthUser from(User user) {
            return new CachedAuthUser(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getPassword(),
                    user.getProfileImage());
        }

        private User toUser() {
            return User.builder()
                    .id(id)
                    .email(email)
                    .name(name)
                    .password(password)
                    .profileImage(profileImage)
                    .build();
        }
    }
}
