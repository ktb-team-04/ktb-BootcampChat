package com.ktb.chatapp.service;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
<<<<<<< HEAD
=======
import org.springframework.data.mongodb.core.MongoTemplate;
>>>>>>> main
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = "socketio.enabled=false")
@DisplayName("최근 메시지 수 Redis 캐시 통합 테스트")
class RecentMessageCounterIntegrationTest {

    private static final String KEY_PREFIX = "chat:recent-message-count:";

    @Autowired private RecentMessageCounter recentMessageCounter;
    @Autowired private MessageRepository messageRepository;
    @Autowired private StringRedisTemplate redisTemplate;
<<<<<<< HEAD
=======
    @Autowired private MongoTemplate mongoTemplate;
>>>>>>> main

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("여러 방의 캐시 미스를 한 번에 집계하고 Redis에 저장한다")
    void countRecentMessages_LoadsMultipleRoomsAndCachesZeros() {
<<<<<<< HEAD
=======
        assertThat(mongoTemplate.indexOps(Message.class).getIndexInfo())
                .anySatisfy(index -> assertThat(index.getName()).isEqualTo("room_timestamp_idx"));
>>>>>>> main
        messageRepository.saveAll(List.of(
                message("room-1", LocalDateTime.now().minusMinutes(2)),
                message("room-1", LocalDateTime.now().minusMinutes(1)),
                message("room-2", LocalDateTime.now().minusMinutes(3))));
        Message oldMessage = messageRepository.save(message("room-1", LocalDateTime.now()));
        oldMessage.setTimestamp(LocalDateTime.now().minusMinutes(31));
        messageRepository.save(oldMessage);

        Map<String, Integer> counts = recentMessageCounter.countRecentMessages(
                List.of("room-1", "room-2", "room-empty"));

        assertThat(counts).containsEntry("room-1", 2)
                .containsEntry("room-2", 1)
                .containsEntry("room-empty", 0);
        assertThat(redisTemplate.opsForValue().multiGet(List.of(
                        KEY_PREFIX + "room-1",
                        KEY_PREFIX + "room-2",
                        KEY_PREFIX + "room-empty")))
                .containsExactly("2", "1", "0");

        messageRepository.deleteAll();
        assertThat(recentMessageCounter.countRecentMessages(
                List.of("room-1", "room-2", "room-empty")))
                .containsEntry("room-1", 2)
                .containsEntry("room-2", 1)
                .containsEntry("room-empty", 0);
    }

    @Test
    @DisplayName("동시 메시지 증가는 Redis에서 손실 없이 원자 처리된다")
    void recordMessageAndGetCount_ConcurrentIncrementsAreAtomic() throws Exception {
        String roomId = "room-concurrent";
        int attempts = 60;
        assertThat(recentMessageCounter.countRecentMessages(roomId)).isZero();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return recentMessageCounter.recordMessageAndGetCount(roomId);
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                result.get();
            }

            assertThat(redisTemplate.opsForValue().get(KEY_PREFIX + roomId))
                    .isEqualTo(Integer.toString(attempts));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("캐시가 없으면 저장 완료된 메시지를 MongoDB에서 계산한다")
    void recordMessageAndGetCount_OnCacheMissLoadsMongo() {
        messageRepository.save(message("room-cold", LocalDateTime.now()));

        assertThat(recentMessageCounter.recordMessageAndGetCount("room-cold")).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(KEY_PREFIX + "room-cold")).isEqualTo("1");
    }

    private Message message(String roomId, LocalDateTime timestamp) {
        return Message.builder()
                .roomId(roomId)
                .senderId("user-1")
                .content("test")
                .type(MessageType.text)
                .timestamp(timestamp)
                .build();
    }
}
