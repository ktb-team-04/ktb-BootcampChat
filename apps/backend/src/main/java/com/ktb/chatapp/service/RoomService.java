package com.ktb.chatapp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.event.RoomCreatedEvent;
import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RoomService {

    /** 방 목록 캐시는 사용자와 무관하게 단일 엔트리로 유지한다. */
    private static final String SHARED_ROOM_LIST_KEY = "all";

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RecentMessageCounter recentMessageCounter;
    private final ChatLookupCache chatLookupCache;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Cache<String, RoomsResponse> roomListCache;
    private final int roomListLimit;

    public RoomService(
            RoomRepository roomRepository,
            UserRepository userRepository,
            RecentMessageCounter recentMessageCounter,
            ChatLookupCache chatLookupCache,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.room-list.cache.ttl:2s}") Duration roomListCacheTtl,
            @Value("${app.room-list.limit:50}") int roomListLimit) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.recentMessageCounter = recentMessageCounter;
        this.chatLookupCache = chatLookupCache;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.roomListLimit = Math.max(1, roomListLimit);
        this.roomListCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(roomListCacheTtl)
                .recordStats()
                .build();
    }

    public RoomsResponse getAllRooms(String name) {
        try {
            // 무거운 조회(Mongo + 참가자 조인 + 최근 메시지 집계)는 전체 인스턴스에서 1건만 캐시하고,
            // 요청자마다 달라지는 isCreator만 캐시된 결과 위에서 다시 계산한다.
            RoomsResponse shared = roomListCache.get(SHARED_ROOM_LIST_KEY, ignored -> loadAllRooms());
            return withCreatorFlag(shared, name);

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);
            return RoomsResponse.builder()
                .success(false)
                .data(List.of())
                .build();
        }
    }

    private RoomsResponse loadAllRooms() {
        // 최신순 상위 N개만 조회한다. 방이 무한히 쌓여도 응답 크기와 조회 비용이 일정하게 유지된다.
        List<Room> rooms = roomRepository.findRecentRooms(PageRequest.of(0, roomListLimit));
        // 모든 방의 사용자 정보를 한 번에 읽어 방/참가자 수에 비례하는 N+1 조회를 피한다.
        Map<String, User> usersById = loadUsersById(rooms);
        Map<String, Integer> recentMessageCounts = recentMessageCounter.countRecentMessages(
                rooms.stream().map(Room::getId).toList());
        List<RoomResponse> roomResponses = rooms.stream()
            .map(room -> mapToRoomResponse(
                    room,
                    null,
                    usersById,
                    recentMessageCounts.getOrDefault(room.getId(), 0)))
            .sorted(Comparator.comparing(
                RoomResponse::getCreatedAtDateTime,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        PageMetadata metadata = PageMetadata.builder()
            .total(roomResponses.size())
            .page(0)
            .pageSize(roomResponses.size())
            .totalPages(1)
            .hasMore(false)
            .currentCount(roomResponses.size())
            .build();

        return RoomsResponse.builder()
            .success(true)
            .data(roomResponses)
            .metadata(metadata)
            .build();
    }

    /**
     * 공유 캐시에는 isCreator=false로 담기므로, 요청한 사용자 기준으로 그 값만 다시 채운다.
     * 추가 조회 없이 리스트를 얕게 다시 만드는 작업이라 비용이 사실상 없다.
     */
    private RoomsResponse withCreatorFlag(RoomsResponse shared, String name) {
        if (shared == null || shared.getData() == null) {
            return shared;
        }

        List<RoomResponse> personalized = shared.getData().stream()
            .map(room -> RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .hasPassword(room.isHasPassword())
                .creator(room.getCreator())
                .participants(room.getParticipants())
                .createdAtDateTime(room.getCreatedAtDateTime())
                .recentMessageCount(room.getRecentMessageCount())
                .isCreator(isCreatedBy(room, name))
                .build())
            .collect(Collectors.toList());

        return RoomsResponse.builder()
            .success(shared.isSuccess())
            .data(personalized)
            .metadata(shared.getMetadata())
            .build();
    }

    private boolean isCreatedBy(RoomResponse room, String name) {
        return name != null
                && room.getCreator() != null
                && name.equalsIgnoreCase(room.getCreator().getEmail());
    }

    public HealthResponse getHealthStatus() {
        try {
            long startTime = System.currentTimeMillis();

            // MongoDB 연결 상태 확인
            boolean isMongoConnected = false;
            long latency = 0;

            try {
                // 간단한 쿼리로 연결 상태 및 지연 시간 측정
                roomRepository.findOneForHealthCheck();
                long endTime = System.currentTimeMillis();
                latency = endTime - startTime;
                isMongoConnected = true;
            } catch (Exception e) {
                log.warn("MongoDB 연결 확인 실패", e);
                isMongoConnected = false;
            }

            // 최근 활동 조회
            LocalDateTime lastActivity = roomRepository.findMostRecentRoom()
                    .map(Room::getCreatedAt)
                    .orElse(null);

            // 서비스 상태 정보 구성
            Map<String, HealthResponse.ServiceHealth> services = new HashMap<>();
            services.put("database", HealthResponse.ServiceHealth.builder()
                .connected(isMongoConnected)
                .latency(latency)
                .build());

            return HealthResponse.builder()
                .success(true)
                .services(services)
                .lastActivity(lastActivity)
                .build();

        } catch (Exception e) {
            log.error("Health check 실행 중 에러 발생", e);
            return HealthResponse.builder()
                .success(false)
                .services(new HashMap<>())
                .build();
        }
    }

    public Room createRoom(CreateRoomRequest createRoomRequest, String name) {
        User creator = userRepository.findByEmail(name)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        Room room = new Room();
        room.setName(createRoomRequest.getName().trim());
        room.setCreator(creator.getId());
        room.getParticipantIds().add(creator.getId());

        if (createRoomRequest.getPassword() != null && !createRoomRequest.getPassword().isEmpty()) {
            room.setHasPassword(true);
            room.setPassword(passwordEncoder.encode(createRoomRequest.getPassword()));
        }

        Room savedRoom = roomRepository.save(room);
        chatLookupCache.invalidateRoom(savedRoom.getId());
        invalidateRoomListCache();
        
        // Publish event for room created
        try {
            RoomResponse roomResponse = mapToRoomResponse(savedRoom, name);
            eventPublisher.publishEvent(new RoomCreatedEvent(this, roomResponse));
        } catch (Exception e) {
            log.error("roomCreated 이벤트 발행 실패", e);
        }
        
        return savedRoom;
    }

    public Optional<Room> findRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public Room joinRoom(String roomId, String password, String name) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return null;
        }

        Room room = roomOpt.get();
        User user = userRepository.findByEmail(name)
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + name));

        // 비밀번호 확인
        if (room.isHasPassword()) {
            if (password == null || !passwordEncoder.matches(password, room.getPassword())) {
                throw new RuntimeException("비밀번호가 일치하지 않습니다.");
            }
        }

        boolean participantAdded = false;

        // 이미 참여중인지 확인
        if (!room.getParticipantIds().contains(user.getId())) {
            // 채팅방 참여
            room.getParticipantIds().add(user.getId());
            room = roomRepository.save(room);
            chatLookupCache.invalidateRoom(room.getId());
            invalidateRoomListCache();
            participantAdded = true;
        }
        
        if (participantAdded) {
            // Publish event for room updated
            try {
                RoomResponse roomResponse = mapToRoomResponse(room, name);
                eventPublisher.publishEvent(new RoomUpdatedEvent(this, roomId, roomResponse));
            } catch (Exception e) {
                log.error("roomUpdate 이벤트 발행 실패", e);
            }
        }

        return room;
    }

    private void invalidateRoomListCache() {
        roomListCache.invalidateAll();
    }

    private RoomResponse mapToRoomResponse(Room room, String name) {
        if (room == null) return null;
        return mapToRoomResponse(
                room,
                name,
                loadUsersById(List.of(room)),
                recentMessageCounter.countRecentMessages(room.getId()));
    }

    private RoomResponse mapToRoomResponse(
            Room room,
            String name,
            Map<String, User> usersById,
            int recentMessageCount) {
        if (room == null) return null;

        User creator = usersById.get(room.getCreator());

        List<User> participants = room.getParticipantIds().stream()
            .map(usersById::get)
            .filter(java.util.Objects::nonNull)
            .toList();

        return RoomResponse.builder()
            .id(room.getId())
            .name(room.getName() != null ? room.getName() : "제목 없음")
            .hasPassword(room.isHasPassword())
            .creator(creator != null ? UserResponse.builder()
                .id(creator.getId())
                .name(creator.getName() != null ? creator.getName() : "알 수 없음")
                .email(creator.getEmail() != null ? creator.getEmail() : "")
                .build() : null)
            .participants(participants.stream()
                .filter(p -> p != null && p.getId() != null)
                .map(p -> UserResponse.builder()
                    .id(p.getId())
                    .name(p.getName() != null ? p.getName() : "알 수 없음")
                    .email(p.getEmail() != null ? p.getEmail() : "")
                    .build())
                .collect(Collectors.toList()))
            .createdAtDateTime(room.getCreatedAt())
            .isCreator(creator != null && creator.getEmail().equalsIgnoreCase(name))
            .recentMessageCount(recentMessageCount)
            .build();
    }

    private Map<String, User> loadUsersById(List<Room> rooms) {
        Set<String> userIds = new HashSet<>();
        for (Room room : rooms) {
            if (room.getCreator() != null) {
                userIds.add(room.getCreator());
            }
            if (room.getParticipantIds() != null) {
                userIds.addAll(room.getParticipantIds());
            }
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
