package com.ktb.chatapp.service;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        "socketio.enabled=false"
})
class MessageReadStatusServiceIntegrationTest {

    @Autowired private MessageRepository messageRepository;
    @Autowired private MessageReadStatusService messageReadStatusService;

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
    }

    @Test
    void updateReadStatus_addsReaderToAllUnreadMessagesWithoutDuplication() {
        Message unread = saveMessage("unread", new ArrayList<>());
        Message alreadyRead = saveMessage("already-read", new ArrayList<>(List.of(
                Message.MessageReader.builder()
                        .userId("user-1")
                        .readAt(LocalDateTime.now().minusMinutes(1))
                        .build())));

        messageReadStatusService.updateReadStatus(
                List.of(unread.getId(), alreadyRead.getId()), "user-1");

        List<Message> updated = messageRepository.findAllById(
                List.of(unread.getId(), alreadyRead.getId()));
        assertThat(updated).hasSize(2).allSatisfy(message ->
                assertThat(message.getReaders())
                        .filteredOn(reader -> reader.getUserId().equals("user-1"))
                        .hasSize(1));
    }

    private Message saveMessage(String content, List<Message.MessageReader> readers) {
        return messageRepository.save(Message.builder()
                .roomId("room-1")
                .senderId("sender-1")
                .content(content)
                .type(MessageType.text)
                .timestamp(LocalDateTime.now())
                .readers(readers)
                .build());
    }
}
