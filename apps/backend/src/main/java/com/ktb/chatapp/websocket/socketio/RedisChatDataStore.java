package com.ktb.chatapp.websocket.socketio;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

/** Redis를 공유하는 모든 Socket.IO 노드에서 접속 사용자와 사용자별 방 상태를 조회한다. */
public class RedisChatDataStore implements ChatDataStore {

    private static final String VALUE_SEGMENT = "value:";
    private static final String SET_SEGMENT = "set:";
    private static final String INDEX_SEGMENT = "index";
    private static final DefaultRedisScript<Long> SET_VALUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1], KEYS[2])
            redis.call('SREM', KEYS[3], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ADD_TO_SET_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('SADD', KEYS[2], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_FROM_SET_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SREM', KEYS[1], ARGV[1])
            local remaining = redis.call('SCARD', KEYS[1])
            if remaining == 0 then
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[2])
            end
            return remaining
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisChatDataStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(valueKey(key));
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, type));
        } catch (Exception e) {
            throw new IllegalStateException("Socket.IO 분산 상태를 읽을 수 없습니다: " + key, e);
        }
    }

    @Override
    public void set(String key, Object value) {
        try {
            redisTemplate.execute(
                    SET_VALUE_SCRIPT,
                    List.of(valueKey(key), indexKey(key)),
                    objectMapper.writeValueAsString(value),
                    key);
        } catch (Exception e) {
            throw new IllegalStateException("Socket.IO 분산 상태를 저장할 수 없습니다: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            redisTemplate.execute(
                    DELETE_SCRIPT,
                    List.of(valueKey(key), setKey(key), indexKey(key)),
                    key);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Socket.IO 분산 상태를 삭제할 수 없습니다: " + key, e);
        }
    }

    @Override
    public int size(String logicalKeyPrefix) {
        Long size = redisTemplate.opsForSet().size(indexKey(logicalKeyPrefix));
        return size == null ? 0 : Math.toIntExact(size);
    }

    @Override
    public Set<String> getSet(String key) {
        Set<String> values = redisTemplate.opsForSet().members(setKey(key));
        return values == null ? Set.of() : Set.copyOf(values);
    }

    @Override
    public void addToSet(String key, String value) {
        try {
            redisTemplate.execute(
                    ADD_TO_SET_SCRIPT,
                    List.of(setKey(key), indexKey(key)),
                    value,
                    key);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Socket.IO 방 상태를 추가할 수 없습니다: " + key, e);
        }
    }

    @Override
    public void removeFromSet(String key, String value) {
        try {
            redisTemplate.execute(
                    REMOVE_FROM_SET_SCRIPT,
                    List.of(setKey(key), indexKey(key)),
                    value,
                    key);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Socket.IO 방 상태를 삭제할 수 없습니다: " + key, e);
        }
    }

    private String valueKey(String key) {
        return keyPrefix + VALUE_SEGMENT + key;
    }

    private String setKey(String key) {
        return keyPrefix + SET_SEGMENT + key;
    }

    private String indexKey(String logicalKeyOrPrefix) {
        int categoryEnd = logicalKeyOrPrefix.endsWith(":")
                ? logicalKeyOrPrefix.length()
                : logicalKeyOrPrefix.lastIndexOf(':') + 1;
        String category = categoryEnd > 0
                ? logicalKeyOrPrefix.substring(0, categoryEnd)
                : logicalKeyOrPrefix;
        return keyPrefix + INDEX_SEGMENT + ":" + category;
    }
}
