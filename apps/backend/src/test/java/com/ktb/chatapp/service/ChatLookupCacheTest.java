package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ChatLookupCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserRepository userRepository;
    @Mock private RoomRepository roomRepository;

    private final AtomicReference<String> cachedValue = new AtomicReference<>();
    private ChatLookupCache chatLookupCache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenAnswer(ignored -> cachedValue.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            cachedValue.set(invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(any(), any(), any(Duration.class));

        chatLookupCache = new ChatLookupCache(
                redisTemplate, JsonMapper.builder().build(), userRepository, roomRepository);
        ReflectionTestUtils.setField(chatLookupCache, "userKeyPrefix", "test:user:");
        ReflectionTestUtils.setField(chatLookupCache, "roomKeyPrefix", "test:room:");
        ReflectionTestUtils.setField(chatLookupCache, "cacheTtl", Duration.ofMinutes(5));
        ReflectionTestUtils.setField(chatLookupCache, "negativeCacheTtl", Duration.ofSeconds(15));
    }

    @Test
    void findUser_usesRedisAfterMongoLookup() {
        User user = User.builder()
                .id("user-1")
                .name("사용자")
                .email("user@example.com")
                .profileImage("profiles/user.png")
                .build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThat(chatLookupCache.findUser("user-1")).isPresent();
        assertThat(chatLookupCache.findUser("user-1").orElseThrow().getName()).isEqualTo("사용자");

        verify(userRepository, times(1)).findById("user-1");
        verify(valueOperations).set(eq("test:user:user-1"), any(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void canAccessRoom_usesCachedParticipantIds() {
        Room room = Room.builder()
                .id("room-1")
                .participantIds(Set.of("user-1"))
                .build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThat(chatLookupCache.canAccessRoom("room-1", "user-1")).isTrue();
        assertThat(chatLookupCache.canAccessRoom("room-1", "user-2")).isFalse();

        verify(roomRepository, times(1)).findById("room-1");
        verify(valueOperations).set(eq("test:room:room-1"), any(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void missingRoom_usesShortNegativeCache() {
        when(roomRepository.findById("missing-room")).thenReturn(Optional.empty());

        assertThat(chatLookupCache.canAccessRoom("missing-room", "user-1")).isFalse();
        assertThat(chatLookupCache.canAccessRoom("missing-room", "user-1")).isFalse();

        verify(roomRepository, times(1)).findById("missing-room");
        verify(valueOperations).set(
                eq("test:room:missing-room"), any(), eq(Duration.ofSeconds(15)));
    }
}
