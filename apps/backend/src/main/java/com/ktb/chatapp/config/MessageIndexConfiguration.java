package com.ktb.chatapp.config;

import com.ktb.chatapp.model.Message;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

/** 최근 메시지 집계와 메시지 페이지 조회가 공유하는 MongoDB 인덱스를 보장한다. */
@Configuration
public class MessageIndexConfiguration {

    @Bean
    ApplicationRunner ensureMessageIndexes(MongoTemplate mongoTemplate) {
        return args -> mongoTemplate.indexOps(Message.class).createIndex(
                new Index()
                        .on("room", Direction.ASC)
                        .on("timestamp", Direction.ASC)
                        .named("room_timestamp_idx"));
    }
}
