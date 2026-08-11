package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.repository.MessageRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecentMessageCounterTest {

    @Mock private MessageRepository messageRepository;

    @Test
    void countsMultipleRoomsWithOneRepositoryCall() {
        MessageRepository.RoomMessageCount first = count("room-1", 7);
        MessageRepository.RoomMessageCount second = count("room-2", 3);
        when(messageRepository.countRecentMessagesByRoomIds(eq(List.of("room-1", "room-2")), any()))
                .thenReturn(List.of(first, second));

        Map<String, Integer> result = new RecentMessageCounter(messageRepository)
                .countRecentMessages(List.of("room-1", "room-2"));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("room-1", 7, "room-2", 3));
        verify(messageRepository).countRecentMessagesByRoomIds(eq(List.of("room-1", "room-2")), any());
    }

    @Test
    void skipsMongoForEmptyRoomList() {
        Map<String, Integer> result = new RecentMessageCounter(messageRepository)
                .countRecentMessages(List.of());

        assertThat(result).isEmpty();
        verify(messageRepository, never()).countRecentMessagesByRoomIds(any(), any());
    }

    @Test
    void treatsNullAggregationResultAsNoRecentMessages() {
        when(messageRepository.countRecentMessagesByRoomIds(eq(List.of("room-1")), any()))
                .thenReturn(null);

        Map<String, Integer> result = new RecentMessageCounter(messageRepository)
                .countRecentMessages(List.of("room-1"));

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresNullAggregationRows() {
        MessageRepository.RoomMessageCount count = count("room-1", 2);
        when(messageRepository.countRecentMessagesByRoomIds(eq(List.of("room-1")), any()))
                .thenReturn(java.util.Arrays.asList(null, count));

        Map<String, Integer> result = new RecentMessageCounter(messageRepository)
                .countRecentMessages(List.of("room-1"));

        assertThat(result).containsExactlyEntriesOf(Map.of("room-1", 2));
    }

    private MessageRepository.RoomMessageCount count(String id, long value) {
        return new MessageRepository.RoomMessageCount() {
            @Override
            public String getRoomId() {
                return id;
            }

            @Override
            public long getCount() {
                return value;
            }
        };
    }
}
