package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageReadStatusService;
import com.ktb.chatapp.service.RecentRoomMessageCache;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLoader {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;
    private final RecentRoomMessageCache recentRoomMessageCache;

    /**
     * 메시지 로드
     */
    public FetchMessagesResponse loadMessages(FetchMessagesRequest data, String userId) {
        try {
            int maxFirstPageSize = recentRoomMessageCache.maxSize();
            int limit = Math.min(data.limit(maxFirstPageSize), maxFirstPageSize);
            boolean firstPage = data.before() == null;
            if (firstPage) {
                var cached = recentRoomMessageCache.getFirstPage(data.roomId(), limit);
                if (cached.isPresent()) {
                    var cachedMessages = cached.orElseThrow();
                    updateReadStatusFromResponses(cachedMessages.messages(), userId);
                    return FetchMessagesResponse.builder()
                            .messages(cachedMessages.messages())
                            .hasMore(cachedMessages.hasMore())
                            .build();
                }
            }

            FetchMessagesResponse response =
                    loadMessagesInternal(data.roomId(), limit, data.before(LocalDateTime.now()), userId);
            if (firstPage) {
                recentRoomMessageCache.cacheFirstPage(
                        data.roomId(), response.getMessages(), response.isHasMore());
            }
            return response;
        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", data.roomId(), e);
            return FetchMessagesResponse.builder()
                    .messages(emptyList())
                    .hasMore(false)
                    .build();
        }
    }

    private FetchMessagesResponse loadMessagesInternal(
            String roomId,
            int limit,
            LocalDateTime before,
            String userId) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").descending());

        Page<Message> messagePage = messageRepository
                .findByRoomIdAndTimestampBefore(roomId, before, pageable);

        List<Message> messages = messagePage.getContent();

        // DESC로 조회했으므로 ASC로 재정렬 (채팅 UI 표시 순서)
        List<Message> sortedMessages = messages.reversed();

        updateReadStatus(sortedMessages.stream()
                .map(Message::getId)
                .toList(), userId);

        // 발신자를 메시지마다 조회하지 않고 현재 배치에 필요한 사용자만 한 번에 읽는다.
        Set<String> senderIds = sortedMessages.stream()
                .map(Message::getSenderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> sendersById = senderIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        // 파일 정보 역시 매퍼에서 현재 메시지 배치에 필요한 항목만 일괄 조회한다.
        List<MessageResponse> messageResponses =
                messageResponseMapper.mapToMessageResponses(sortedMessages, sendersById);

        boolean hasMore = messagePage.hasNext();

        log.debug("Messages loaded - roomId: {}, limit: {}, count: {}, hasMore: {}",
                roomId, limit, messageResponses.size(), hasMore);

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .build();
    }

    private void updateReadStatusFromResponses(List<MessageResponse> messages, String userId) {
        updateReadStatus(messages.stream()
                .map(MessageResponse::getId)
                .toList(), userId);
    }

    private void updateReadStatus(List<String> messageIds, String userId) {
        messageReadStatusService.updateReadStatus(messageIds, userId);
    }

}
