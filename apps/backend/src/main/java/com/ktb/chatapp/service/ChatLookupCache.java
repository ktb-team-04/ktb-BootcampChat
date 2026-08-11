package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 채팅 전송 경로에서 반복되는 사용자와 방 참여 여부 조회를 Redis에 캐시한다.
 * 비밀번호 등 채팅 처리에 필요하지 않은 정보는 캐시에 저장하지 않는다.
 */
@Slf4j
@Service
public class ChatLookupCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public ChatLookupCache(
            @Qualifier("cacheRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            RoomRepository roomRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    @Value("${app.chat-lookup.cache.user-key-prefix:chat:lookup:user:}")
    private String userKeyPrefix;

    @Value("${app.chat-lookup.cache.room-key-prefix:chat:lookup:room:}")
    private String roomKeyPrefix;

    @Value("${app.chat-lookup.cache.ttl:5m}")
    private Duration cacheTtl;

    @Value("${app.chat-lookup.cache.negative-ttl:15s}")
    private Duration negativeCacheTtl;

    public Optional<UserResponse> findUser(String userId) {
        String key = userKeyPrefix + userId;
        CachedUser cached = read(key, CachedUser.class);
        if (cached != null) {
            return cached.found() ? Optional.of(cached.toResponse()) : Optional.empty();
        }

        Optional<User> user = userRepository.findById(userId);
        write(key, user.map(CachedUser::found).orElseGet(CachedUser::missing));
        return user.map(UserResponse::from);
    }

    public boolean canAccessRoom(String roomId, String userId) {
        String key = roomKeyPrefix + roomId;
        CachedRoom cached = read(key, CachedRoom.class);
        if (cached != null) {
            return cached.found() && cached.participantIds().contains(userId);
        }

        Optional<Room> room = roomRepository.findById(roomId);
        write(key, room.map(CachedRoom::found).orElseGet(CachedRoom::missing));
        return room.map(value -> value.getParticipantIds().contains(userId)).orElse(false);
    }

    public void invalidateUser(String userId) {
        delete(userKeyPrefix + userId);
    }

    public void invalidateRoom(String roomId) {
        delete(roomKeyPrefix + roomId);
    }

    private <T> T read(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("채팅 조회 캐시 읽기 실패 - key: {}", key, e);
            return null;
        }
    }

    private void write(String key, CacheEntry entry) {
        try {
            Duration ttl = entry.found() ? cacheTtl : negativeCacheTtl;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl);
        } catch (Exception e) {
            log.warn("채팅 조회 캐시 저장 실패 - key: {}", key, e);
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("채팅 조회 캐시 무효화 실패 - key: {}", key, e);
        }
    }

    private interface CacheEntry {
        boolean found();
    }

    private record CachedUser(
            boolean found,
            String id,
            String name,
            String email,
            String profileImage) implements CacheEntry {

        private static CachedUser found(User user) {
            UserResponse response = UserResponse.from(user);
            return new CachedUser(
                    true,
                    response.getId(),
                    response.getName(),
                    response.getEmail(),
                    response.getProfileImage());
        }

        private static CachedUser missing() {
            return new CachedUser(false, null, null, null, null);
        }

        private UserResponse toResponse() {
            return UserResponse.builder()
                    .id(id)
                    .name(name)
                    .email(email)
                    .profileImage(profileImage)
                    .build();
        }
    }

    private record CachedRoom(boolean found, Set<String> participantIds) implements CacheEntry {

        private static CachedRoom found(Room room) {
            Set<String> participantIds = room.getParticipantIds() == null
                    ? Set.of()
                    : Set.copyOf(room.getParticipantIds());
            return new CachedRoom(true, participantIds);
        }

        private static CachedRoom missing() {
            return new CachedRoom(false, Set.of());
        }
    }
}
