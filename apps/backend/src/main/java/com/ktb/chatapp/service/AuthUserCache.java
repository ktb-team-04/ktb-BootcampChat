package com.ktb.chatapp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb.chatapp.model.User;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded local caches for the single-instance authentication hot path. */
@Component
public class AuthUserCache {

    private final Cache<String, User> users;
    private final Cache<String, Boolean> missingUsers;

    public AuthUserCache(
            @Value("${app.auth.user-cache.maximum-size:20000}") long userMaximumSize,
            @Value("${app.auth.user-cache.ttl:15m}") Duration userTtl,
            @Value("${app.auth.negative-cache.maximum-size:5000}") long missingMaximumSize,
            @Value("${app.auth.negative-cache.ttl:1m}") Duration missingTtl) {
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
    }

    public User get(String email) {
        return users.getIfPresent(email);
    }

    public boolean isMissing(String email) {
        return missingUsers.getIfPresent(email) != null;
    }

    public void put(User user) {
        users.put(user.getEmail(), user);
        missingUsers.invalidate(user.getEmail());
    }

    public void markMissing(String email) {
        missingUsers.put(email, Boolean.TRUE);
    }
}
