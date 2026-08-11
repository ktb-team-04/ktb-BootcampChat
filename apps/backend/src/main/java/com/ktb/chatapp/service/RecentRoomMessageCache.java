package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.MessageResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 채팅방 입장 직후 첫 화면에 보여줄 최근 메시지만 Redis에 짧게 보관한다.
 */
@Slf4j
@Service
public class RecentRoomMessageCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RecentRoomMessageCache(
            @Qualifier("cacheRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${app.room-message.cache.key-prefix:chat:room:}")
    private String keyPrefix;

    @Value("${app.room-message.cache.max-size:15}")
    private int maxSize;

    @Value("${app.room-message.cache.ttl:3m}")
    private Duration cacheTtl;

    @Value("${app.room-message.cache.empty-ttl:30s}")
    private Duration emptyCacheTtl;

    public int maxSize() {
        return Math.max(1, maxSize);
    }

    public Optional<CachedMessages> getFirstPage(String roomId, int limit) {
        String key = messageKey(roomId);
        try {
            List<String> values = redisTemplate.opsForList().range(key, 0, Math.min(limit, maxSize()) - 1);
            if (values != null && !values.isEmpty()) {
                List<MessageResponse> messages = new ArrayList<>(values.size());
                for (String value : values) {
                    messages.add(objectMapper.readValue(value, MessageResponse.class));
                }
                Collections.reverse(messages);
                boolean hasMore = Boolean.parseBoolean(redisTemplate.opsForValue().get(hasMoreKey(roomId)));
                return Optional.of(new CachedMessages(messages, hasMore));
            }

            if (Boolean.TRUE.equals(redisTemplate.hasKey(emptyKey(roomId)))) {
                return Optional.of(new CachedMessages(List.of(), false));
            }
        } catch (Exception e) {
            log.warn("최근 채팅 메시지 캐시 조회 실패 - roomId: {}", roomId, e);
        }
        return Optional.empty();
    }

    public void cacheFirstPage(String roomId, List<MessageResponse> messages, boolean hasMore) {
        try {
            if (messages.isEmpty()) {
                redisTemplate.delete(messageKey(roomId));
                redisTemplate.delete(hasMoreKey(roomId));
                redisTemplate.opsForValue().set(emptyKey(roomId), "1", emptyCacheTtl);
                return;
            }

            List<String> newestFirst = new ArrayList<>(messages.size());
            for (MessageResponse message : messages.reversed()) {
                newestFirst.add(objectMapper.writeValueAsString(message));
            }

            String key = messageKey(roomId);
            redisTemplate.delete(emptyKey(roomId));
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, newestFirst);
            redisTemplate.opsForList().trim(key, 0, maxSize() - 1);
            redisTemplate.expire(key, cacheTtl);
            redisTemplate.opsForValue().set(hasMoreKey(roomId), Boolean.toString(hasMore), cacheTtl);
        } catch (Exception e) {
            log.warn("최근 채팅 메시지 캐시 저장 실패 - roomId: {}", roomId, e);
        }
    }

    public void append(String roomId, MessageResponse message) {
        try {
            String key = messageKey(roomId);
            boolean hasCachedMessages = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            boolean hasEmptyMarker = Boolean.TRUE.equals(redisTemplate.hasKey(emptyKey(roomId)));
            if (!hasCachedMessages && !hasEmptyMarker) {
                return;
            }

            Long sizeAfterPush = redisTemplate.opsForList()
                    .leftPush(key, objectMapper.writeValueAsString(message));
            redisTemplate.opsForList().trim(key, 0, maxSize() - 1);
            redisTemplate.expire(key, cacheTtl);
            redisTemplate.delete(emptyKey(roomId));

            boolean hasMore = sizeAfterPush != null && sizeAfterPush > maxSize();
            redisTemplate.opsForValue().set(hasMoreKey(roomId), Boolean.toString(hasMore), cacheTtl);
        } catch (Exception e) {
            log.warn("최근 채팅 메시지 캐시 갱신 실패 - roomId: {}", roomId, e);
        }
    }

    private String messageKey(String roomId) {
        return keyPrefix + roomId + ":recent-messages";
    }

    private String emptyKey(String roomId) {
        return messageKey(roomId) + ":empty";
    }

    private String hasMoreKey(String roomId) {
        return messageKey(roomId) + ":has-more";
    }

    public record CachedMessages(List<MessageResponse> messages, boolean hasMore) {}
}
