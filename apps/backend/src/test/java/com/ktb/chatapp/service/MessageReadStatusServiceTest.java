package com.ktb.chatapp.service;

import com.mongodb.client.result.UpdateResult;
import com.ktb.chatapp.model.Message;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageReadStatusServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    @Test
    void updateReadStatus_updatesUnreadMessagesInOneOperation() {
        MessageReadStatusService service = new MessageReadStatusService(mongoTemplate);
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(2, 2L, null));

        service.updateReadStatus(List.of("message-1", "message-2"), "user-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(
                queryCaptor.capture(), updateCaptor.capture(), eq(Message.class));

        assertThat(queryCaptor.getValue().getQueryObject().toJson())
                .contains("message-1", "message-2", "readers.userId", "user-1", "$ne");
        Document addToSet = updateCaptor.getValue().getUpdateObject()
                .get("$addToSet", Document.class);
        assertThat(addToSet).containsKey("readers");
        assertThat(addToSet.get("readers"))
                .isInstanceOfSatisfying(Message.MessageReader.class, reader ->
                        assertThat(reader.getUserId()).isEqualTo("user-1"));
    }

    @Test
    void updateReadStatus_skipsEmptyMessageList() {
        MessageReadStatusService service = new MessageReadStatusService(mongoTemplate);

        service.updateReadStatus(List.of(), "user-1");

        verify(mongoTemplate, never())
                .updateMulti(any(Query.class), any(Update.class), eq(Message.class));
    }
}
