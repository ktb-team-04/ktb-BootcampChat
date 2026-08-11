package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 최근 30분 메시지 수를 짧게 캐시한다.
 *
 * <p>방 목록은 Redis MGET 한 번과 캐시 미스 방에 대한 MongoDB aggregation 한 번만 사용한다.
 * 캐시가 존재하는 동안 새 메시지는 Lua INCR로 반영하며 TTL은 연장하지 않아 주기적으로
 * MongoDB 값과 다시 동기화한다.
 */
@Slf4j
@Component
public class RecentMessageCounter {

    static final Duration RECENT_WINDOW = Duration.ofMinutes(30);

    private static final String INCREMENT_IF_CACHED_SCRIPT_TEXT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            return redis.call('INCR', KEYS[1])
            """;
    private static final DefaultRedisScript<Long> INCREMENT_IF_CACHED_SCRIPT =
            new DefaultRedisScript<>(INCREMENT_IF_CACHED_SCRIPT_TEXT, Long.class);

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration cacheTtl;

    public RecentMessageCounter(
            MongoTemplate mongoTemplate,
            StringRedisTemplate redisTemplate,
            @Value("${app.recent-message.cache.key-prefix:chat:recent-message-count:}") String keyPrefix,
            @Value("${app.recent-message.cache.ttl:5s}") Duration cacheTtl) {
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.cacheTtl = cacheTtl;
        if (cacheTtl.toSeconds() < 1) {
            throw new IllegalArgumentException("Recent message cache TTL must be at least one second");
        }
    }

    public int countRecentMessages(String roomId) {
        if (roomId == null) {
            return 0;
        }
        return countRecentMessages(List.of(roomId)).getOrDefault(roomId, 0);
    }

    public Map<String, Integer> countRecentMessages(Collection<String> roomIds) {
        Set<String> uniqueRoomIds = new LinkedHashSet<>();
        for (String roomId : roomIds) {
            if (roomId != null) {
                uniqueRoomIds.add(roomId);
            }
        }
        if (uniqueRoomIds.isEmpty()) {
            return Map.of();
        }

        List<String> ids = List.copyOf(uniqueRoomIds);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> misses = new LinkedHashSet<>();

        try {
            List<String> cachedValues = redisTemplate.opsForValue().multiGet(keys(ids));
            for (int i = 0; i < ids.size(); i++) {
                String value = cachedValues != null ? cachedValues.get(i) : null;
                if (value == null) {
                    misses.add(ids.get(i));
                } else {
                    try {
                        long parsed = Long.parseLong(value);
                        if (parsed < 0) {
                            misses.add(ids.get(i));
                        } else {
                            counts.put(ids.get(i), toInt(parsed));
                        }
                    } catch (NumberFormatException e) {
                        misses.add(ids.get(i));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("최근 메시지 수 Redis 조회 실패, MongoDB로 조회합니다", e);
            misses.addAll(ids);
        }

        if (!misses.isEmpty()) {
            Map<String, Integer> loaded = loadCountsFromMongo(misses);
            counts.putAll(loaded);
            cacheCounts(loaded);
        }

        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (String roomId : ids) {
            ordered.put(roomId, counts.getOrDefault(roomId, 0));
        }
        return ordered;
    }

    /**
     * 저장이 완료된 메시지를 캐시에 반영하고 이벤트에 사용할 현재 카운트를 반환한다.
     * 캐시 미스면 MongoDB에서 한 번 다시 계산해 캐시를 시작한다.
     */
    public int recordMessageAndGetCount(String roomId) {
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_IF_CACHED_SCRIPT,
                    List.of(key(roomId)));
            if (count != null && count >= 0) {
                return toInt(count);
            }
        } catch (Exception e) {
            log.warn("최근 메시지 수 Redis 증가 실패, MongoDB로 조회합니다: roomId={}", roomId, e);
        }
        return countRecentMessages(roomId);
    }

    private Map<String, Integer> loadCountsFromMongo(Collection<String> roomIds) {
        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("room").in(roomIds).and("timestamp").gte(since)),
                Aggregation.group("room").count().as("count"));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String roomId : roomIds) {
            counts.put(roomId, 0);
        }
        for (Document result : mongoTemplate
                .aggregate(aggregation, Message.class, Document.class)
                .getMappedResults()) {
            Object roomId = result.get("_id");
            Number count = result.get("count", Number.class);
            if (roomId != null && count != null) {
                counts.put(roomId.toString(), toInt(count.longValue()));
            }
        }
        return counts;
    }

    private void cacheCounts(Map<String, Integer> counts) {
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    byte[] key = redisTemplate.getStringSerializer().serialize(key(entry.getKey()));
                    byte[] value = redisTemplate.getStringSerializer().serialize(entry.getValue().toString());
                    connection.stringCommands().setEx(key, cacheTtl.toSeconds(), value);
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("최근 메시지 수 Redis 캐시 저장 실패", e);
        }
    }

    private List<String> keys(List<String> roomIds) {
        return roomIds.stream().map(this::key).toList();
    }

    private String key(String roomId) {
        return keyPrefix + roomId;
    }

    private static int toInt(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public Map<String, Integer> countRecentMessages(Collection<String> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }

        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        List<MessageRepository.RoomMessageCount> counts =
                messageRepository.countRecentMessagesByRoomIds(roomIds, since);

        // Mongo aggregation은 매칭 문서가 없거나 projection을 만들지 못한 경우
        // 구현체/버전에 따라 null 또는 null 원소를 돌려줄 수 있다. 최근 메시지 수는
        // 부가 정보이므로 이 경우 방 목록 전체를 실패시키지 않고 0으로 취급한다.
        if (counts == null || counts.isEmpty()) {
            return Map.of();
        }

        return counts.stream()
                // 과거 문서나 예상하지 못한 aggregation 결과 한 건 때문에
                // 전체 채팅방 목록 조회가 실패하지 않게 한다.
                .filter(Objects::nonNull)
                .filter(count -> count.getRoomId() != null)
                .collect(Collectors.toUnmodifiableMap(
                        MessageRepository.RoomMessageCount::getRoomId,
                        count -> Math.toIntExact(count.getCount()),
                        Integer::sum));
    }
}
