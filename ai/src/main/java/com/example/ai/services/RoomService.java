package com.example.ai.services;

import com.example.ai.dto.CreateProtectedRoomRequest;
import com.example.ai.dto.MessageResponse;
import com.example.ai.dto.RoomMemberResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.dto.UpdateRoomRequest;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMember;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.RoomNotificationSetting;
import com.example.ai.entity.User;
import com.example.ai.repository.MessageMentionRepository;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.RoomNotificationSettingRepository;
import com.example.ai.repository.RoomRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomNotificationSettingRepository notificationSettingRepository;
    private final MessageMentionRepository messageMentionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final MediaService mediaService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public RoomResponse createRoom(CreateProtectedRoomRequest request, String userId) {
        String hashedPassword = (request.password() != null && !request.password().isBlank())
                ? passwordEncoder.encode(request.password())
                : null;

        Room room = Room.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name().trim())
                .type("GROUP")
                .passwordHash(hashedPassword)
                .isPrivate(request.isPrivate() != null && request.isPrivate())
                .createdBy(userId)
                .build();
        roomRepository.save(room);

        RoomMember member = RoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .role("OWNER")
                .build();
        roomMemberRepository.save(member);

        return toRoomResponse(room);
    }

    public List<RoomResponse> getAllRooms() {
        return roomRepository.findPublicRooms(PageRequest.of(0, 100)).getContent().stream()
                .map(this::toRoomResponse)
                .toList();
    }

    public List<RoomResponse> getJoinedRooms(String userId) {
        Map<String, Room> rooms = new LinkedHashMap<>();
        roomRepository.findJoinedRoomsByUserId(userId).forEach(r -> rooms.putIfAbsent(r.getId(), r));
        roomRepository.findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .forEach(r -> rooms.putIfAbsent(r.getId(), r));

        Map<String, RoomNotificationSetting> settingsByRoom = new HashMap<>();
        notificationSettingRepository.findByUserId(userId).stream()
                .filter(s -> rooms.containsKey(s.getRoomId()))
                .forEach(s -> settingsByRoom.put(s.getRoomId(), s));

        return rooms.values().stream()
                .map(room -> toRoomResponse(room, userId, settingsByRoom.get(room.getId())))
                .toList();
    }

    public RoomResponse getRoom(String roomId) {
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .map(this::toRoomResponse)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    }

    public List<RoomResponse> searchRooms(String query) {
        return roomRepository.findPublicRoomsByNameContaining(query, PageRequest.of(0, 100))
                .getContent().stream()
                .map(this::toRoomResponse)
                .toList();
    }

    public List<MessageResponse> getMessages(String roomId) {
        List<RoomMessage> messages = new java.util.ArrayList<>(
                roomMessageRepository.findTop200ByRoomIdOrderByCreatedAtDesc(roomId)
        );
        Collections.reverse(messages);

        Set<String> senderIds = messages.stream()
                .map(RoomMessage::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> userNames = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        Set<Long> replyIds = messages.stream()
                .map(RoomMessage::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, RoomMessage> repliesById = replyIds.isEmpty() ? Collections.emptyMap()
                : roomMessageRepository.findAllById(replyIds).stream()
                        .collect(Collectors.toMap(RoomMessage::getId, m -> m));

        return messages.stream().map(msg -> {
            boolean deleted = msg.getDeletedAt() != null;
            Long replyToId = msg.getReplyToId();
            RoomMessage reply = replyToId != null ? repliesById.get(replyToId) : null;
            String replyToSenderName = null;
            String replyToContent = null;
            if (reply != null) {
                replyToSenderName = userNames.getOrDefault(reply.getSenderId(), "Unknown");
                if (reply.getDeletedAt() != null) {
                    replyToContent = null;
                } else if (reply.getContent() != null) {
                    replyToContent = reply.getContent();
                } else if (reply.getMediaUrl() != null) {
                    replyToContent = "[" + mediaTypeLabel(reply.getMessageType()) + "]";
                }
            }

            return new MessageResponse(
                    msg.getId(),
                    msg.getSenderId(),
                    userNames.getOrDefault(msg.getSenderId(), "Unknown"),
                    deleted ? null : msg.getContent(),
                    msg.getMessageType(),
                    msg.getMediaUrl(),
                    msg.getCreatedAt(),
                    replyToId,
                    replyToSenderName,
                    replyToContent,
                    deleted
            );
        }).toList();
    }

    public void joinRoom(String roomId, String userId, String password) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isCreator = userId.equals(room.getCreatedBy());
        boolean isAlreadyMember = roomMemberRepository.existsByRoomIdAndUserId(roomId, userId);

        // Skip password check if user is the room creator OR already a joined member
        if (room.getPasswordHash() != null && !isCreator && !isAlreadyMember) {
            if (password == null || !passwordEncoder.matches(password, room.getPasswordHash())) {
                throw new IllegalArgumentException("Incorrect room password");
            }
        }

        if (!isAlreadyMember) {
            RoomMember member = RoomMember.builder()
                    .roomId(roomId)
                    .userId(userId)
                    .role(isCreator ? "OWNER" : "MEMBER")
                    .build();
            roomMemberRepository.save(member);

            if (room.getCreatedBy() != null && !room.getCreatedBy().equals(userId)) {
                String joinerName = userRepository.findById(userId)
                        .map(User::getDisplayName)
                        .orElse("Someone");
                notificationService.create(
                        room.getCreatedBy(),
                        "ROOM_JOINED",
                        joinerName + " joined your room",
                        room.getName(),
                        userId,
                        roomId
                );
            }
        }
    }

    @Transactional
    public void deleteRoom(String roomId, User user) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOwner = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Unauthorized to delete this room. Only room owners or admins can delete rooms.");
        }

        room.setDeletedAt(Instant.now());
        roomRepository.save(room);
    }

    @Transactional
    public void kickMember(String roomId, String targetUserId, User user) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOwner = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Unauthorized to kick members. Only room owners or admins can kick members.");
        }

        if (targetUserId.equals(user.getId())) {
            throw new IllegalArgumentException("Cannot kick yourself");
        }

        if (targetUserId.equals(room.getCreatedBy())) {
            throw new IllegalArgumentException("Cannot kick the room owner");
        }

        roomMemberRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
    }

    @Transactional
    public void updateRoom(String roomId, UpdateRoomRequest request, User user) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOwner = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("Unauthorized to update this room. Only room owners or admins can update room settings.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            room.setName(request.name().trim());
        }

        if (request.password() != null) {
            if (request.password().isBlank()) {
                room.setPasswordHash(null);
            } else {
                room.setPasswordHash(passwordEncoder.encode(request.password()));
            }
        }

        roomRepository.save(room);
    }

    public List<RoomMemberResponse> getMembers(String roomId, User user) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isCreator = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        boolean isMember = roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getId());

        if (!isMember && !isCreator && !isAdmin) {
            throw new IllegalArgumentException("Only room members can view the member list");
        }

        List<RoomMember> members = roomMemberRepository.findByRoomId(roomId);
        Map<String, User> usersById = userRepository.findAllById(
                        members.stream().map(RoomMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return members.stream().map(m -> {
            User u = usersById.get(m.getUserId());
            return new RoomMemberResponse(
                    m.getUserId(),
                    u != null ? u.getUsername() : "unknown",
                    u != null ? u.getDisplayName() : "Unknown",
                    u != null ? u.getAvatarUrl() : null,
                    m.getRole(),
                    m.getJoinedAt()
            );
        }).toList();
    }

    @Transactional
    public void deleteMessage(String roomId, Long messageId, User user) {
        RoomMessage msg = roomMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));

        if (!msg.getRoomId().equals(roomId)) {
            throw new IllegalArgumentException("Message not found in this room");
        }

        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isSender = msg.getSenderId().equals(user.getId());
        boolean isOwner = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());

        if (!isSender && !isOwner && !isAdmin) {
            throw new IllegalArgumentException("You can only delete your own messages");
        }

        if (msg.getDeletedAt() != null) {
            return;
        }

        String mediaUrl = msg.getMediaUrl();
        msg.setContent(null);
        msg.setDeletedAt(Instant.now());
        roomMessageRepository.save(msg);

        if (mediaUrl != null) {
            mediaService.deleteObject(mediaUrl);
        }
    }

    private String mediaTypeLabel(String messageType) {
        if (messageType == null) return "File";
        return switch (messageType.toUpperCase()) {
            case "IMAGE" -> "Image";
            case "AUDIO" -> "Audio";
            case "FILE" -> "File";
            default -> "File";
        };
    }

    public Map<String, String> getInviteLink(String roomId, User user) {
        Room room = roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        boolean isOwner = room.getCreatedBy() != null && room.getCreatedBy().equals(user.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        boolean isMember = roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getId());

        if (!isOwner && !isAdmin && !isMember) {
            throw new IllegalArgumentException("Only room members can generate an invite link");
        }

        return Map.of("url", frontendBaseUrl + "/invite/" + room.getId());
    }

    private RoomResponse toRoomResponse(Room r) {
        return new RoomResponse(
                r.getId(),
                r.getName(),
                r.getType(),
                r.getPasswordHash() != null,
                r.getCreatedBy(),
                r.getCreatedAt(),
                r.isPrivate(),
                0,
                RoomNotificationSetting.MODE_ALL
        );
    }

    private RoomResponse toRoomResponse(Room r, String userId, RoomNotificationSetting setting) {
        String mode = setting != null ? setting.getMode() : RoomNotificationSetting.MODE_ALL;
        long unread = computeUnreadCount(r.getId(), userId, mode, setting);
        return new RoomResponse(
                r.getId(),
                r.getName(),
                r.getType(),
                r.getPasswordHash() != null,
                r.getCreatedBy(),
                r.getCreatedAt(),
                r.isPrivate(),
                unread,
                mode
        );
    }

    private long computeUnreadCount(String roomId, String userId, String mode, RoomNotificationSetting setting) {
        if (RoomNotificationSetting.MODE_MUTED.equals(mode)) return 0;

        Instant since = setting != null && setting.getLastReadAt() != null
                ? setting.getLastReadAt()
                : Instant.EPOCH;

        if (RoomNotificationSetting.MODE_MENTIONS_ONLY.equals(mode)) {
            return messageMentionRepository.countUnreadMentions(userId, roomId, since);
        }
        return notificationSettingRepository.countUnreadMessages(userId, roomId, since);
    }
}
