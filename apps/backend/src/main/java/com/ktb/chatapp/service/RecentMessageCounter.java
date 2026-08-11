package com.ktb.chatapp.service;

import com.ktb.chatapp.repository.MessageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 채팅방 목록에 노출하는 "최근 메시지 수"의 집계 창을 한곳에서 관리한다.
 */
@Component
@RequiredArgsConstructor
public class RecentMessageCounter {

    static final Duration RECENT_WINDOW = Duration.ofMinutes(30);

    private final MessageRepository messageRepository;

    public int countRecentMessages(String roomId) {
        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        return (int) messageRepository.countRecentMessagesByRoomId(roomId, since);
    }

    public Map<String, Integer> countRecentMessages(Collection<String> roomIds) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }

        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        List<MessageRepository.RoomMessageCount> counts =
                messageRepository.countRecentMessagesByRoomIds(roomIds, since);

        // Mongo aggregation은 매칭 문서가 없거나 projection을 만들지 못한 경우
        // 구현체/버전에 따라 null 또는 null 원소를 돌려줄 수 있다. 최근 메시지 수는
        // 부가 정보이므로 이 경우 방 목록 전체를 실패시키지 않고 0으로 취급한다.
        if (counts == null || counts.isEmpty()) {
            return Map.of();
        }

        return counts.stream()
                // 과거 문서나 예상하지 못한 aggregation 결과 한 건 때문에
                // 전체 채팅방 목록 조회가 실패하지 않게 한다.
                .filter(Objects::nonNull)
                .filter(count -> count.getRoomId() != null)
                .collect(Collectors.toUnmodifiableMap(
                        MessageRepository.RoomMessageCount::getRoomId,
                        count -> Math.toIntExact(count.getCount()),
                        Integer::sum));
    }
}
