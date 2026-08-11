package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RoomsResponse;
import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService 단위 테스트")
class RoomServiceUnitTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private ChatLookupCache chatLookupCache;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                chatLookupCache,
                passwordEncoder,
                eventPublisher,
                Duration.ofSeconds(2),
                1000);
    }

    @Test
    @DisplayName("방 목록의 생성자와 참가자를 한 번의 사용자 조회로 변환한다")
    void getAllRooms_LoadsUsersInOneBatch() {
        User first = User.builder().id("user-1").name("first").email("first@example.com").build();
        User second = User.builder().id("user-2").name("second").email("second@example.com").build();
        Room firstRoom = room("room-1", "user-1", Set.of("user-1", "user-2"));
        Room secondRoom = room("room-2", "user-2", Set.of("user-2"));

        when(roomRepository.findAll()).thenReturn(List.of(firstRoom, secondRoom));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(first, second));
        when(recentMessageCounter.countRecentMessages(List.of("room-1", "room-2")))
                .thenReturn(Map.of("room-1", 7, "room-2", 3));

        RoomsResponse response = roomService.getAllRooms("first@example.com");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(2);
        assertThat(response.getData()).anySatisfy(room -> {
            if (room.getId().equals("room-1")) {
                assertThat(room.isCreator()).isTrue();
                assertThat(room.getParticipants()).hasSize(2);
                assertThat(room.getRecentMessageCount()).isEqualTo(7);
            }
        });
        verify(userRepository, times(1)).findAllById(anySet());
        verify(recentMessageCounter, times(1))
                .countRecentMessages(List.of("room-1", "room-2"));
    }

    @Test
    @DisplayName("방 목록은 사용자별 로컬 캐시를 사용한다")
    void getAllRooms_UsesLocalCachePerUser() {
        User first = User.builder().id("user-1").name("first").email("first@example.com").build();
        Room firstRoom = room("room-1", "user-1", Set.of("user-1"));

        when(roomRepository.findAll()).thenReturn(List.of(firstRoom));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(first));
        when(recentMessageCounter.countRecentMessages(List.of("room-1")))
                .thenReturn(Map.of("room-1", 7));

        roomService.getAllRooms("first@example.com");
        roomService.getAllRooms("first@example.com");

        verify(roomRepository, times(1)).findAll();
        verify(userRepository, times(1)).findAllById(anySet());
        verify(recentMessageCounter, times(1))
                .countRecentMessages(List.of("room-1"));
    }

    @Test
    @DisplayName("이미 참가 중인 사용자의 중복 방 입장은 room update 이벤트를 발행하지 않는다")
    void joinRoom_DoesNotPublishRoomUpdatedEventWhenParticipantAlreadyJoined() {
        User user = User.builder().id("user-1").name("first").email("first@example.com").build();
        Room room = room("room-1", "user-1", Set.of("user-1"));

        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("first@example.com")).thenReturn(Optional.of(user));

        Room joinedRoom = roomService.joinRoom("room-1", null, "first@example.com");

        assertThat(joinedRoom).isSameAs(room);
        verify(roomRepository, never()).save(any(Room.class));
        verify(eventPublisher, never()).publishEvent(any(RoomUpdatedEvent.class));
    }

    private Room room(String id, String creatorId, Set<String> participantIds) {
        return Room.builder()
                .id(id)
                .name(id)
                .creator(creatorId)
                .participantIds(participantIds)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
