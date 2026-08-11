package com.ktb.chatapp.service.message;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BufferedMessageWriteServiceTest {

    @Test
    void write_buffersTextMessagesWhenQueueHasCapacity() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        BufferedMessageWriteService service =
                new BufferedMessageWriteService(messageRepository, "buffered", 10, 1);
        Message message = textMessage("room-1", "hello");

        Message result = service.write(message, true);

        assertSame(message, result);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void write_savesSynchronouslyWhenBufferIsFull() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        BufferedMessageWriteService service =
                new BufferedMessageWriteService(messageRepository, "buffered", 10, 1);
        Message queuedMessage = textMessage("room-1", "first");
        Message overflowMessage = textMessage("room-1", "second");
        Message savedOverflowMessage = textMessage("room-1", "second");
        savedOverflowMessage.setId("persisted-message");
        when(messageRepository.save(overflowMessage)).thenReturn(savedOverflowMessage);

        service.write(queuedMessage, true);
        Message result = service.write(overflowMessage, true);

        assertSame(savedOverflowMessage, result);
        verify(messageRepository).save(overflowMessage);
    }

    private Message textMessage(String roomId, String content) {
        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId("user-1");
        message.setContent(content);
        message.setType(MessageType.text);
        return message;
    }
}
