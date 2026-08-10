package com.ktb.chatapp.config;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoConnectionPoolCustomizer(
            @Value("${app.mongodb.pool.max-size:100}") int maxSize,
            @Value("${app.mongodb.pool.min-size:10}") int minSize,
            @Value("${app.mongodb.pool.max-wait-ms:2000}") long maxWaitMs) {
        return settings -> settings.applyToConnectionPoolSettings(pool -> pool
                .maxSize(maxSize)
                .minSize(minSize)
                .maxWaitTime(maxWaitMs, TimeUnit.MILLISECONDS));
    }
}
