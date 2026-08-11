package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.dto.MessageResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RecentRoomMessageCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private ValueOperations<String, String> valueOperations;

    private RecentRoomMessageCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new RecentRoomMessageCache(redisTemplate, JsonMapper.builder().build());
        ReflectionTestUtils.setField(cache, "keyPrefix", "test:room:");
        ReflectionTestUtils.setField(cache, "maxSize", 15);
        ReflectionTestUtils.setField(cache, "cacheTtl", Duration.ofMinutes(3));
        ReflectionTestUtils.setField(cache, "emptyCacheTtl", Duration.ofSeconds(30));
    }

    @Test
    void cacheFirstPage_storesEmptyMarkerForEmptyMessages() {
        cache.cacheFirstPage("room-1", List.of(), false);

        verify(redisTemplate).delete("test:room:room-1:recent-messages");
        verify(redisTemplate).delete("test:room:room-1:recent-messages:has-more");
        verify(valueOperations).set(
                "test:room:room-1:recent-messages:empty", "1", Duration.ofSeconds(30));
    }

    @Test
    void getFirstPage_returnsEmptyResultFromEmptyMarker() {
        when(listOperations.range("test:room:room-1:recent-messages", 0, 14))
                .thenReturn(List.of());
        when(redisTemplate.hasKey("test:room:room-1:recent-messages:empty"))
                .thenReturn(true);

        Optional<RecentRoomMessageCache.CachedMessages> result = cache.getFirstPage("room-1", 15);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().messages()).isEmpty();
        assertThat(result.orElseThrow().hasMore()).isFalse();
    }

    @Test
    void append_skipsWhenCacheWasNotWarmed() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        cache.append("room-1", message("message-1"));

        verify(listOperations, never()).leftPush(anyString(), anyString());
    }

    @Test
    void append_updatesExistingRecentMessageList() {
        when(redisTemplate.hasKey("test:room:room-1:recent-messages")).thenReturn(true);
        when(redisTemplate.hasKey("test:room:room-1:recent-messages:empty")).thenReturn(false);
        when(listOperations.leftPush(eq("test:room:room-1:recent-messages"), anyString()))
                .thenReturn(16L);

        cache.append("room-1", message("message-1"));

        verify(listOperations).trim("test:room:room-1:recent-messages", 0, 14);
        verify(redisTemplate).expire("test:room:room-1:recent-messages", Duration.ofMinutes(3));
        verify(redisTemplate).delete("test:room:room-1:recent-messages:empty");
        verify(valueOperations).set(
                "test:room:room-1:recent-messages:has-more", "true", Duration.ofMinutes(3));
    }

    private MessageResponse message(String id) {
        return MessageResponse.builder()
                .id(id)
                .roomId("room-1")
                .content("hello")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
