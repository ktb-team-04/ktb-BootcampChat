package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.JoinRoomRequest;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.RecentMessageCounter;
import com.ktb.chatapp.service.RoomService;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController 단위 테스트")
class RoomControllerUnitTest {

    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private RoomService roomService;
    @Mock private Principal principal;

    @InjectMocks private RoomController roomController;

    @Test
    @DisplayName("방 비밀번호 불일치는 인증 만료가 아닌 403으로 응답한다")
    void joinRoom_PasswordMismatch_ReturnsForbidden() {
        JoinRoomRequest request = new JoinRoomRequest("wrong-password");
        when(principal.getName()).thenReturn("user@example.com");
        when(roomService.joinRoom("room-1", "wrong-password", "user@example.com"))
                .thenThrow(new RuntimeException("비밀번호가 일치하지 않습니다."));

        ResponseEntity<?> response = roomController.joinRoom("room-1", request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
