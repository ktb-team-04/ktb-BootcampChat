package com.ktb.chatapp.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RoleRedisConfig {

    @Bean(name = {"redisConnectionFactory", "stateRedisConnectionFactory"})
    @Primary
    RedisConnectionFactory stateRedisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled) {
        return connectionFactory(host, port, username, password, database, sslEnabled);
    }

    @Bean(name = {"stringRedisTemplate", "stateRedisTemplate"})
    @Primary
    StringRedisTemplate stateRedisTemplate(
            @Qualifier("stateRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "socketRedisConnectionFactory")
    RedisConnectionFactory socketRedisConnectionFactory(
            @Value("${socketio.redis.host}") String host,
            @Value("${socketio.redis.port}") int port,
            @Value("${socketio.redis.username:}") String username,
            @Value("${socketio.redis.password:}") String password,
            @Value("${socketio.redis.database:0}") int database,
            @Value("${socketio.redis.ssl.enabled:false}") boolean sslEnabled) {
        return connectionFactory(host, port, username, password, database, sslEnabled);
    }

    @Bean(name = "socketRedisTemplate")
    StringRedisTemplate socketRedisTemplate(
            @Qualifier("socketRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "cacheRedisConnectionFactory")
    RedisConnectionFactory cacheRedisConnectionFactory(
            @Value("${app.cache.redis.host}") String host,
            @Value("${app.cache.redis.port}") int port,
            @Value("${app.cache.redis.username:}") String username,
            @Value("${app.cache.redis.password:}") String password,
            @Value("${app.cache.redis.database:0}") int database,
            @Value("${app.cache.redis.ssl.enabled:false}") boolean sslEnabled) {
        return connectionFactory(host, port, username, password, database, sslEnabled);
    }

    @Bean(name = "cacheRedisTemplate")
    StringRedisTemplate cacheRedisTemplate(
            @Qualifier("cacheRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    private RedisConnectionFactory connectionFactory(
            String host,
            int port,
            String username,
            String password,
            int database,
            boolean sslEnabled) {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(host, port);
        redisConfig.setDatabase(database);
        if (!username.isBlank()) {
            redisConfig.setUsername(username);
        }
        if (!password.isBlank()) {
            redisConfig.setPassword(RedisPassword.of(password));
        }

        LettuceClientConfiguration clientConfig = sslEnabled
                ? LettuceClientConfiguration.builder().useSsl().build()
                : LettuceClientConfiguration.builder().build();
        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }
}
