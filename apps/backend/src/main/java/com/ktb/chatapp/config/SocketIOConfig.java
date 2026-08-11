package com.ktb.chatapp.config;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.corundumstudio.socketio.store.MemoryStoreFactory;
import com.corundumstudio.socketio.store.RedissonStoreFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ktb.chatapp.websocket.socketio.ChatDataStore;
import com.ktb.chatapp.websocket.socketio.LocalChatDataStore;
import com.ktb.chatapp.websocket.socketio.RedisChatDataStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.Redisson;
import org.redisson.config.Config;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOConfig {

    @Value("${socketio.server.host:localhost}")
    private String host;

    @Value("${socketio.server.port:5002}")
    private Integer port;

    @Value("${socketio.server.origin:*}")
    private String origin;

    @Value("${socketio.server.accept-backlog:200}")
    private int acceptBacklog;

    @Value("${socketio.store.type:redis}")
    private String storeType;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean redisSslEnabled;

    @Value("${socketio.store.redis.connection-pool-size:16}")
    private int redisConnectionPoolSize;

    @Value("${socketio.store.redis.subscription-pool-size:8}")
    private int redisSubscriptionPoolSize;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SocketIOServer socketIOServer(AuthTokenListener authTokenListener, MeterRegistry meterRegistry) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname(host);
        config.setPort(port);
        
        var socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        socketConfig.setTcpNoDelay(true);
        socketConfig.setAcceptBackLog(acceptBacklog);
        config.setSocketConfig(socketConfig);

        config.setOrigin(origin);

        // Socket.IO settings
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        config.setUpgradeTimeout(10000);

        config.setJsonSupport(new JacksonJsonSupport(new JavaTimeModule()));
        config.setStoreFactory(createStoreFactory());

        log.info("Socket.IO server configured on {}:{} with {} store, {} boss threads and {} worker threads",
                 host, port, storeType, config.getBossThreads(), config.getWorkerThreads());
        var socketIOServer = new SocketIOServer(config);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(authTokenListener);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addEventInterceptor((client, name, data, ack) -> {
            // 이벤트 발생 빈도 수집
            Counter.builder("socketio.events.total")
                .description("Total Socket.IO events received")
                .tag("event_type", name)
                .register(meterRegistry)
                .increment();
        });
        
        return socketIOServer;
    }
    
    /**
     * SpringAnnotationScanner는 BeanPostProcessor로서
     * ApplicationContext 초기화 초기에 등록되고,
     * 내부에서 사용하는 SocketIOServer는 Lazy로 지연되어
     * 다른 Bean들의 초기화 과정에 간섭하지 않게 한다.
     */
    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    public BeanPostProcessor springAnnotationScanner(@Lazy SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
    
    @Bean
    @ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
    public ChatDataStore chatDataStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${socketio.store.redis.data-key-prefix:chat:socketio:data:}") String dataKeyPrefix) {
        if ("memory".equalsIgnoreCase(storeType)) {
            return new LocalChatDataStore();
        }
        if ("redis".equalsIgnoreCase(storeType)) {
            return new RedisChatDataStore(redisTemplate, objectMapper, dataKeyPrefix);
        }
        throw new IllegalArgumentException("지원하지 않는 Socket.IO store type: " + storeType);
    }

    private com.corundumstudio.socketio.store.StoreFactory createStoreFactory() {
        if ("memory".equalsIgnoreCase(storeType)) {
            return new MemoryStoreFactory();
        }
        if (!"redis".equalsIgnoreCase(storeType)) {
            throw new IllegalArgumentException("지원하지 않는 Socket.IO store type: " + storeType);
        }

        Config redissonConfig = new Config();
        var singleServer = redissonConfig.useSingleServer()
                .setAddress((redisSslEnabled ? "rediss://" : "redis://") + redisHost + ":" + redisPort)
                .setDatabase(redisDatabase)
                .setConnectionMinimumIdleSize(Math.min(2, redisConnectionPoolSize))
                .setConnectionPoolSize(redisConnectionPoolSize)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(redisSubscriptionPoolSize);
        if (!redisUsername.isBlank()) {
            singleServer.setUsername(redisUsername);
        }
        if (!redisPassword.isBlank()) {
            singleServer.setPassword(redisPassword);
        }
        return new RedissonStoreFactory(Redisson.create(redissonConfig));
    }
}
