package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RecentMessageCounterTest {

    private static final String KEY_PREFIX = "chat:recent-message-count:";

    @Mock private MongoTemplate mongoTemplate;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RecentMessageCounter recentMessageCounter;

    @BeforeEach
    void setUp() {
        recentMessageCounter = new RecentMessageCounter(
                mongoTemplate,
                redisTemplate,
                KEY_PREFIX,
                Duration.ofSeconds(5));
    }

    @Test
    void returnsMultipleRoomCountsFromOneRedisCall() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(KEY_PREFIX + "room-1", KEY_PREFIX + "room-2")))
                .thenReturn(List.of("7", "3"));

        Map<String, Integer> result = recentMessageCounter
                .countRecentMessages(List.of("room-1", "room-2"));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("room-1", 7, "room-2", 3));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void skipsRedisAndMongoForEmptyRoomList() {
        Map<String, Integer> result = recentMessageCounter.countRecentMessages(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(redisTemplate, mongoTemplate);
    }

    @Test
    void ignoresNullAndDuplicateRoomIds() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(List.of(KEY_PREFIX + "room-1")))
                .thenReturn(List.of("2"));

        Map<String, Integer> result = recentMessageCounter
                .countRecentMessages(java.util.Arrays.asList(null, "room-1", "room-1"));

        assertThat(result).containsExactlyEntriesOf(Map.of("room-1", 2));
        verifyNoInteractions(mongoTemplate);
    }
}
