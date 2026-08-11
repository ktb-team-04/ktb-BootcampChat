package com.ktb.chatapp.config;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import com.ktb.chatapp.model.Message;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoConnectionPoolCustomizer(
            @Value("${app.mongodb.pool.max-size:40}") int maxSize, // 조율 필요
            @Value("${app.mongodb.pool.min-size:5}") int minSize, // 조율 필요
            @Value("${app.mongodb.pool.max-wait-ms:1000}") long maxWaitMs) {
        return settings -> settings.applyToConnectionPoolSettings(pool -> pool
                .maxSize(maxSize)
                .minSize(minSize)
                .maxWaitTime(maxWaitMs, TimeUnit.MILLISECONDS));
    }

    @Bean
    ApplicationRunner ensureMessageQueryIndexes(MongoTemplate mongoTemplate) {
        return args -> mongoTemplate.indexOps(Message.class).ensureIndex(
                new Index()
                        .on("room", Direction.ASC)
                        .on("timestamp", Direction.DESC)
                        .named("room_timestamp_idx"));
    }
}
