package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.chatapp.model.User;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthUserCacheTest {

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
}
