package com.ktb.chatapp.service.session;

import com.ktb.chatapp.model.Session;
import com.ktb.chatapp.service.SessionMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Stores one active session per user in Redis.
 *
 * <p>Each write replaces the complete hash and refreshes its TTL atomically. Conditional deletion
 * prevents a logout request carrying an old session ID from deleting a newer login session.
 */
@Component
@ConditionalOnProperty(name = "app.session.store", havingValue = "redis", matchIfMissing = true)
public class SessionRedisStore implements SessionStore {

    private static final String WRITE_SCRIPT_TEXT = """
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1],
                'id', ARGV[1],
                'userId', ARGV[2],
                'sessionId', ARGV[3],
                'createdAt', ARGV[4],
                'lastActivity', ARGV[5],
                'expiresAt', ARGV[6],
                'metadataPresent', ARGV[7],
                'userAgent', ARGV[8],
                'ipAddress', ARGV[9],
                'deviceInfo', ARGV[10])
            redis.call('EXPIRE', KEYS[1], ARGV[11])
            return 1
            """;

    private static final String DELETE_IF_MATCHES_SCRIPT_TEXT = """
            if redis.call('HGET', KEYS[1], 'sessionId') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private static final String SAVE_IF_MATCHES_SCRIPT_TEXT = """
            if redis.call('HGET', KEYS[1], 'sessionId') ~= ARGV[3] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[1],
                'id', ARGV[1],
                'userId', ARGV[2],
                'sessionId', ARGV[3],
                'createdAt', ARGV[4],
                'lastActivity', ARGV[5],
                'expiresAt', ARGV[6],
                'metadataPresent', ARGV[7],
                'userAgent', ARGV[8],
                'ipAddress', ARGV[9],
                'deviceInfo', ARGV[10])
            redis.call('EXPIRE', KEYS[1], ARGV[11])
            return 1
            """;

    private static final DefaultRedisScript<Long> WRITE_SCRIPT =
            new DefaultRedisScript<>(WRITE_SCRIPT_TEXT, Long.class);
    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>(DELETE_IF_MATCHES_SCRIPT_TEXT, Long.class);
    private static final DefaultRedisScript<Long> SAVE_IF_MATCHES_SCRIPT =
            new DefaultRedisScript<>(SAVE_IF_MATCHES_SCRIPT_TEXT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration sessionTtl;

    public SessionRedisStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.session.redis.key-prefix:chat:session:user:}") String keyPrefix,
            @Value("${app.session.redis.ttl:30m}") Duration sessionTtl) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.sessionTtl = sessionTtl;
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive");
        }
    }

    @Override
    public Optional<Session> findByUserId(String userId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key(userId));
        if (values.isEmpty()) {
            return Optional.empty();
        }

        boolean hasMetadata = "1".equals(value(values, "metadataPresent"));
        SessionMetadata metadata = hasMetadata
                ? new SessionMetadata(
                        value(values, "userAgent"),
                        value(values, "ipAddress"),
                        value(values, "deviceInfo"))
                : null;

        return Optional.of(Session.builder()
                .id(nullIfEmpty(value(values, "id")))
                .userId(value(values, "userId"))
                .sessionId(value(values, "sessionId"))
                .createdAt(Long.parseLong(value(values, "createdAt")))
                .lastActivity(Long.parseLong(value(values, "lastActivity")))
                .metadata(metadata)
                .expiresAt(Instant.ofEpochMilli(Long.parseLong(value(values, "expiresAt"))))
                .build());
    }

    @Override
    public Session save(Session session) {
        return write(session, SAVE_IF_MATCHES_SCRIPT);
    }

    @Override
    public Session replace(Session session) {
        return write(session, WRITE_SCRIPT);
    }

    @Override
    public void deleteAll(String userId) {
        redisTemplate.delete(key(userId));
    }

    @Override
    public void delete(String userId, String sessionId) {
        redisTemplate.execute(DELETE_IF_MATCHES_SCRIPT, List.of(key(userId)), sessionId);
    }

    private Session write(Session session, DefaultRedisScript<Long> script) {
        SessionMetadata metadata = session.getMetadata();
        redisTemplate.execute(
                script,
                List.of(key(session.getUserId())),
                emptyIfNull(session.getId()),
                session.getUserId(),
                session.getSessionId(),
                Long.toString(session.getCreatedAt()),
                Long.toString(session.getLastActivity()),
                Long.toString(session.getExpiresAt().toEpochMilli()),
                metadata == null ? "0" : "1",
                metadata == null ? "" : emptyIfNull(metadata.userAgent()),
                metadata == null ? "" : emptyIfNull(metadata.ipAddress()),
                metadata == null ? "" : emptyIfNull(metadata.deviceInfo()),
                Long.toString(sessionTtl.toSeconds()));
        return session;
    }

    String key(String userId) {
        return keyPrefix + userId;
    }

    private static String value(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? "" : value.toString();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
