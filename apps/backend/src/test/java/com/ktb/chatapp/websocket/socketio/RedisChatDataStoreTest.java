package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RedisChatDataStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;

    private RedisChatDataStore dataStore;

    @BeforeEach
    void setUp() {
        dataStore = new RedisChatDataStore(
                redisTemplate,
                JsonMapper.builder().build(),
                "test:socketio:");
    }

    @Test
    void connectedUser_isSerializedAndRestoredFromSharedRedisKey() {
        SocketUser user = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("test:socketio:value:conn_users:userid:user-1"))
                .thenReturn("""
                        {"id":"user-1","name":"tester","authSessionId":"session-1","socketId":"socket-1"}
                        """);

        dataStore.set("conn_users:userid:user-1", user);
        SocketUser restored = dataStore
                .get("conn_users:userid:user-1", SocketUser.class)
                .orElseThrow();

        assertThat(restored).isEqualTo(user);
        verify(redisTemplate).execute(
                any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void roomMembership_usesRedisSetAndCanBeReadByAnotherStoreInstance() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("test:socketio:set:userroom:roomids:user-1"))
                .thenReturn(Set.of("room-1", "room-2"));

        dataStore.addToSet("userroom:roomids:user-1", "room-1");
        dataStore.removeFromSet("userroom:roomids:user-1", "room-2");

        assertThat(dataStore.getSet("userroom:roomids:user-1"))
                .containsExactlyInAnyOrder("room-1", "room-2");
        verify(redisTemplate, org.mockito.Mockito.times(2)).execute(
                any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void size_countsOnlyRequestedLogicalKeyPrefix() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.size("test:socketio:index:conn_users:userid:"))
                .thenReturn(2L);

        assertThat(dataStore.size("conn_users:userid:")).isEqualTo(2);
    }
}
