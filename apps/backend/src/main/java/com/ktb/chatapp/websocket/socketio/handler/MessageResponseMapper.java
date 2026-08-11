package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.service.FileUrl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 메시지를 응답 DTO로 변환하는 매퍼
 * 파일 정보, 사용자 정보 등을 포함한 MessageResponse 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageResponseMapper {

    private final FileRepository fileRepository;

    /**
     * Message 엔티티를 MessageResponse DTO로 변환
     *
     * @param message 변환할 메시지 엔티티
     * @param sender 메시지 발신자 정보 (null 가능)
     * @return MessageResponse DTO
     */
    public MessageResponse mapToMessageResponse(Message message, User sender) {
        File attachedFile = Optional.ofNullable(message.getFileId())
                .flatMap(fileRepository::findById)
                .orElse(null);
        return mapToMessageResponse(message, sender, attachedFile);
    }

    /**
     * 메시지 목록에 필요한 파일을 한 번에 조회하여 응답 목록으로 변환한다.
     */
    public List<MessageResponse> mapToMessageResponses(
            List<Message> messages,
            Map<String, User> sendersById) {
        var fileIds = messages.stream()
                .map(Message::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, File> filesById = fileIds.isEmpty()
                ? Map.of()
                : fileRepository.findAllById(fileIds).stream()
                        .collect(Collectors.toMap(File::getId, Function.identity()));

        return messages.stream()
                .map(message -> mapToMessageResponse(
                        message,
                        message.getSenderId() == null
                                ? null
                                : sendersById.get(message.getSenderId()),
                        message.getFileId() == null
                                ? null
                                : filesById.get(message.getFileId())))
                .toList();
    }

    private MessageResponse mapToMessageResponse(Message message, User sender, File attachedFile) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.toTimestampMillis())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ?
                        message.getReactions() : new HashMap<>())
                .readers(message.getReaders() != null ?
                        message.getReaders() : new ArrayList<>());

        // 발신자 정보 설정
        if (sender != null) {
            builder.sender(UserResponse.builder()
                    .id(sender.getId())
                    .name(sender.getName())
                    .email(sender.getEmail())
                    .profileImage(FileUrl.of(sender.getProfileImage()))
                    .build());
        }

        // 파일 정보 설정
        Optional.ofNullable(attachedFile)
                .map(file -> FileResponse.builder()
                        .id(file.getId())
                        .filename(file.getFilename())
                        .originalname(file.getOriginalname())
                        .mimetype(file.getMimetype())
                        .size(file.getSize())
                        .build())
                .ifPresent(builder::file);

        // 메타데이터 설정
        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }
}
