package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.dto.rooms.CreateRoomRequest;
import com.ktb.chatapp.dto.rooms.HealthResponse;
import com.ktb.chatapp.dto.rooms.RoomResponse;
import com.ktb.chatapp.dto.rooms.RoomsResponse;
import com.ktb.chatapp.dto.rooms.RoomSummary;
import com.ktb.chatapp.dto.user.UserResponse;
import com.ktb.chatapp.event.RoomCreatedEvent;
import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.cache.RoomListCache;
import com.ktb.chatapp.service.cache.RoomListCache.CachedRoomsPage;
import com.ktb.chatapp.service.cache.RoomListCache.RoomListCacheKey;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AddFieldsOperation;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;
    private final RoomListCache roomListCache;

    public RoomsResponse getAllRoomsWithPagination(
            com.ktb.chatapp.dto.PageRequest pageRequest, String name) {

        try {
            // 정렬 설정 검증
            if (!pageRequest.isValidSortField()) {
                pageRequest.setSortField("createdAt");
            }
            if (!pageRequest.isValidSortOrder()) {
                pageRequest.setSortOrder("desc");
            }

            // 정렬 방향 설정
            Sort.Direction direction = "desc".equals(pageRequest.getSortOrder())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

            String sortField = pageRequest.getSortField();

            RoomListCacheKey cacheKey = new RoomListCacheKey(
                pageRequest.getPage(),
                pageRequest.getPageSize(),
                pageRequest.getSortField(),
                pageRequest.getSortOrder(),
                pageRequest.getSearch()
            ).normalize();

            CachedRoomsPage cached = roomListCache.get(cacheKey);
            if (cached != null) {
                return buildResponseFromCache(cached, name);
            }

            // Pageable 객체 생성
            PageRequest springPageRequest = PageRequest.of(
                pageRequest.getPage(),
                pageRequest.getPageSize(),
                Sort.by(direction, sortField)
            );

            List<RoomSummary> roomSummaries = loadRoomSummaries(
                pageRequest.getSearch(),
                springPageRequest.getPageNumber(),
                springPageRequest.getPageSize(),
                direction,
                sortField
            );

            long total = computeTotalCount(pageRequest.getSearch());

            Map<String, User> usersById = loadUsersForRoomSummaries(roomSummaries);
            Map<String, Long> recentCounts = loadRecentMessageCountsByIds(
                roomSummaries.stream().map(RoomSummary::id).toList());

            List<RoomResponse> baseRoomResponses = roomSummaries.stream()
                .map(summary -> mapToRoomResponse(summary, null, usersById, recentCounts))
                .toList();

            // 메타데이터 생성
            PageMetadata metadata = PageMetadata.builder()
                .total(total)
                .page(pageRequest.getPage())
                .pageSize(pageRequest.getPageSize())
                .totalPages(calculateTotalPages(total, pageRequest.getPageSize()))
                .hasMore(hasMorePages(total, pageRequest.getPage(), pageRequest.getPageSize()))
                .currentCount(baseRoomResponses.size())
                .sort(PageMetadata.SortInfo.builder()
                    .field(pageRequest.getSortField())
                    .order(pageRequest.getSortOrder())
                    .build())
                .build();

            roomListCache.put(cacheKey, baseRoomResponses, metadata);

            return RoomsResponse.builder()
                .success(true)
                .data(applyRequesterIdentity(baseRoomResponses, name))
                .metadata(metadata)
                .build();

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);
            return RoomsResponse.builder()
                .success(false)
                .data(List.of())
                .build();
        }
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
        
        // Publish event for room created
        try {
            Map<String, User> usersById = loadUsersForRooms(List.of(savedRoom), true);
            Map<String, Long> recentCounts = loadRecentMessageCounts(List.of(savedRoom));
            RoomResponse roomResponse = mapToRoomResponse(savedRoom, name, usersById, recentCounts, true);
            eventPublisher.publishEvent(new RoomCreatedEvent(this, roomResponse));
        } catch (Exception e) {
            log.error("roomCreated 이벤트 발행 실패", e);
        }
        
        roomListCache.invalidateAll();
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

        // 이미 참여중인지 확인
        if (!room.getParticipantIds().contains(user.getId())) {
            // 채팅방 참여
            room.getParticipantIds().add(user.getId());
            room = roomRepository.save(room);
        }
        
        // Publish event for room updated
        try {
            Map<String, User> usersById = loadUsersForRooms(List.of(room), true);
            Map<String, Long> recentCounts = loadRecentMessageCounts(List.of(room));
            RoomResponse roomResponse = mapToRoomResponse(room, name, usersById, recentCounts, true);
            eventPublisher.publishEvent(new RoomUpdatedEvent(this, roomId, roomResponse));
        } catch (Exception e) {
            log.error("roomUpdate 이벤트 발행 실패", e);
        }

        roomListCache.invalidateAll();
        return room;
    }

    private List<RoomSummary> loadRoomSummaries(
            String search,
            int page,
            int size,
            Sort.Direction direction,
            String sortField) {
        List<AggregationOperation> operations = new ArrayList<>();
        if (StringUtils.hasText(search)) {
            MatchOperation match = Aggregation.match(
                Criteria.where("name").regex(search.trim(), "i"));
            operations.add(match);
        }

        AddFieldsOperation participantsCountField = Aggregation.addFields()
            .addField("participantsCount")
            .withValue(
                ArrayOperators.Size.lengthOfArray(
                    ConditionalOperators.ifNull("participantIds").then(Collections.emptyList())
                )
            )
            .build();
        operations.add(participantsCountField);

        SortOperation sortStage = Aggregation.sort(Sort.by(direction, resolveRoomSortField(sortField)));
        operations.add(sortStage);

        long skipValue = Math.max(page, 0) * (long) Math.max(size, 0);
        operations.add(Aggregation.skip(skipValue));
        operations.add(Aggregation.limit(Math.max(size, 0)));

        ProjectionOperation projection = Aggregation.project("name", "creator", "hasPassword", "createdAt")
            .and("_id").as("id")
            .and("participantsCount").as("participantsCount");
        operations.add(projection);

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<RoomSummary> results =
            mongoTemplate.aggregate(aggregation, "rooms", RoomSummary.class);
        return results.getMappedResults();
    }

    private long computeTotalCount(String search) {
        if (StringUtils.hasText(search)) {
            return roomRepository.countByNameContainingIgnoreCase(search.trim());
        }
        return roomRepository.count();
    }

    private String resolveRoomSortField(String field) {
        if ("participantsCount".equals(field)) {
            return "participantsCount";
        }
        if ("name".equals(field) || "hasPassword".equals(field) || "createdAt".equals(field)) {
            return field;
        }
        return "createdAt";
    }

    private RoomResponse mapToRoomResponse(
            Room room,
            String requesterIdentity,
            Map<String, User> usersById,
            Map<String, Long> recentCounts,
            boolean includeParticipants) {
        if (room == null) return null;

        User creator = room.getCreator() != null ? usersById.get(room.getCreator()) : null;

        List<User> participants = includeParticipants && room.getParticipantIds() != null
            ? room.getParticipantIds().stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .toList()
            : List.of();

        long recentMessageCount = recentCounts.getOrDefault(room.getId(), 0L);

        return RoomResponse.builder()
            .id(room.getId())
            .name(room.getName() != null ? room.getName() : "제목 없음")
            .hasPassword(room.isHasPassword())
            .creator(creator != null ? UserResponse.builder()
                .id(creator.getId())
                .name(creator.getName() != null ? creator.getName() : "알 수 없음")
                .email(creator.getEmail() != null ? creator.getEmail() : "")
                .build() : null)
            .participants(includeParticipants
                ? participants.stream()
                    .filter(p -> p != null && p.getId() != null)
                    .map(p -> UserResponse.builder()
                        .id(p.getId())
                        .name(p.getName() != null ? p.getName() : "알 수 없음")
                        .email(p.getEmail() != null ? p.getEmail() : "")
                        .build())
                    .collect(Collectors.toList())
                : null)
            .participantsCount(room.getParticipantIds() != null ? room.getParticipantIds().size() : 0)
            .createdAtDateTime(room.getCreatedAt())
            .isCreator(creator != null && creator.getEmail() != null &&
                creator.getEmail().equalsIgnoreCase(requesterIdentity))
            .recentMessageCount((int) recentMessageCount)
            .build();
    }

    private RoomResponse mapToRoomResponse(
            RoomSummary summary,
            String requesterIdentity,
            Map<String, User> usersById,
            Map<String, Long> recentCounts) {
        if (summary == null) {
            return null;
        }

        User creator = summary.creator() != null ? usersById.get(summary.creator()) : null;

        return RoomResponse.builder()
            .id(summary.id())
            .name(summary.name() != null ? summary.name() : "제목 없음")
            .hasPassword(summary.hasPassword())
            .creator(creator != null ? UserResponse.builder()
                .id(creator.getId())
                .name(creator.getName() != null ? creator.getName() : "알 수 없음")
                .email(creator.getEmail() != null ? creator.getEmail() : "")
                .build() : null)
            .participants(null)
            .participantsCount((int) summary.participantsCount())
            .createdAtDateTime(summary.createdAt())
            .isCreator(creator != null && creator.getEmail() != null &&
                creator.getEmail().equalsIgnoreCase(requesterIdentity))
            .recentMessageCount(recentCounts.getOrDefault(summary.id(), 0L).intValue())
            .build();
    }

    private Map<String, User> loadUsersForRooms(List<Room> rooms, boolean includeParticipants) {
        if (rooms == null || rooms.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> userIds = new HashSet<>();
        for (Room room : rooms) {
            if (room.getCreator() != null) {
                userIds.add(room.getCreator());
            }
            if (includeParticipants && room.getParticipantIds() != null) {
                userIds.addAll(room.getParticipantIds());
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, User> result = new HashMap<>();
        Iterable<User> users = userRepository.findAllById(userIds);
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    }

    private Map<String, User> loadUsersForRoomSummaries(List<RoomSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> userIds = summaries.stream()
            .map(RoomSummary::creator)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, User> result = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> result.put(user.getId(), user));
        return result;
    }

    private Map<String, Long> loadRecentMessageCounts(List<Room> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> roomIds = rooms.stream()
            .map(Room::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
        if (roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        List<MessageRepository.RoomMessageCount> counts =
            messageRepository.countRecentMessagesByRoomIds(roomIds, tenMinutesAgo);
        Map<String, Long> result = new HashMap<>();
        counts.forEach(count -> result.put(count.getId(), count.getCount()));
        return result;
    }

    private Map<String, Long> loadRecentMessageCountsByIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
        List<MessageRepository.RoomMessageCount> counts =
            messageRepository.countRecentMessagesByRoomIds(roomIds, tenMinutesAgo);
        Map<String, Long> result = new HashMap<>();
        counts.forEach(count -> result.put(count.getId(), count.getCount()));
        return result;
    }

    private int calculateTotalPages(long total, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }

    private boolean hasMorePages(long total, int page, int pageSize) {
        if (pageSize <= 0) {
            return false;
        }
        long shown = (long) (page + 1) * pageSize;
        return shown < total;
    }

    private RoomsResponse buildResponseFromCache(CachedRoomsPage cached, String requesterIdentity) {
        List<RoomResponse> responses = applyRequesterIdentity(cached.rooms(), requesterIdentity);
        return RoomsResponse.builder()
            .success(true)
            .data(responses)
            .metadata(cached.metadata())
            .build();
    }

    private List<RoomResponse> applyRequesterIdentity(List<RoomResponse> baseResponses, String requesterIdentity) {
        if (requesterIdentity == null || requesterIdentity.isBlank()) {
            return baseResponses;
        }
        return baseResponses.stream()
            .map(response -> response.toBuilder()
                .isCreator(isCreatorForRequester(response, requesterIdentity))
                .build())
            .toList();
    }

    private boolean isCreatorForRequester(RoomResponse response, String requesterIdentity) {
        if (response.getCreator() == null || response.getCreator().getEmail() == null) {
            return false;
        }
        return response.getCreator().getEmail().equalsIgnoreCase(requesterIdentity);
    }
}
