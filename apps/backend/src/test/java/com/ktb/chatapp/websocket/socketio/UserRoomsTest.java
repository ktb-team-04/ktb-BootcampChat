package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRoomsTest {

    @Mock private ChatDataStore chatDataStore;

    @Test
    void roomChanges_delegateToAtomicSetOperations() {
        UserRooms userRooms = new UserRooms(chatDataStore);
        when(chatDataStore.getSet("userroom:roomids:user-1"))
                .thenReturn(Set.of("room-1"));

        userRooms.add("user-1", "room-2");
        userRooms.remove("user-1", "room-1");

        assertThat(userRooms.isInRoom("user-1", "room-1")).isTrue();
        verify(chatDataStore).addToSet("userroom:roomids:user-1", "room-2");
        verify(chatDataStore).removeFromSet("userroom:roomids:user-1", "room-1");
    }

    @Test
    void removeAllRooms_deletesTheSharedSetOnce() {
        UserRooms userRooms = new UserRooms(chatDataStore);

        userRooms.removeAllRooms("user-1");

        verify(chatDataStore).delete("userroom:roomids:user-1");
    }
}
