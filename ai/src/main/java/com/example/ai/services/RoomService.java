package com.example.ai.services;

import com.example.ai.dto.CreateProtectedRoomRequest;
import com.example.ai.dto.MessageResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMember;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.RoomRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RoomResponse createRoom(CreateProtectedRoomRequest request, String userId) {
        String hashedPassword = (request.password() != null && !request.password().isBlank())
                ? passwordEncoder.encode(request.password())
                : null;

        Room room = Room.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name().trim())
                .type("GROUP")
                .passwordHash(hashedPassword)
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
        return roomRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .map(this::toRoomResponse)
                .toList();
    }

    public List<RoomResponse> getJoinedRooms(String userId) {
        List<RoomMember> members = roomMemberRepository.findByUserId(userId);
        List<String> joinedRoomIds = new java.util.ArrayList<>(members.stream().map(RoomMember::getRoomId).toList());

        List<Room> createdRooms = roomRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(r -> userId.equals(r.getCreatedBy()))
                .toList();
        for (Room r : createdRooms) {
            if (!joinedRoomIds.contains(r.getId())) {
                joinedRoomIds.add(r.getId());
            }
        }

        return roomRepository.findAllById(joinedRoomIds).stream()
                .filter(r -> r.getDeletedAt() == null)
                .map(this::toRoomResponse)
                .toList();
    }

    public List<RoomResponse> searchRooms(String query) {
        return roomRepository.findByNameContainingIgnoreCaseAndDeletedAtIsNull(query).stream()
                .map(this::toRoomResponse)
                .toList();
    }

    public List<MessageResponse> getMessages(String roomId) {
        List<RoomMessage> messages = roomMessageRepository.findByRoomIdAndDeletedAtIsNullOrderByCreatedAtAsc(roomId);
        Map<String, String> userNames = new HashMap<>();

        return messages.stream().map(msg -> {
            String senderName = userNames.computeIfAbsent(msg.getSenderId(), id ->
                userRepository.findById(id).map(User::getDisplayName).orElse("Unknown")
            );
            return new MessageResponse(
                    msg.getId(),
                    msg.getSenderId(),
                    senderName,
                    msg.getContent(),
                    msg.getMessageType(),
                    msg.getMediaUrl(),
                    msg.getCreatedAt()
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

        roomMemberRepository.deleteByRoomIdAndUserId(roomId, targetUserId);
    }

    private RoomResponse toRoomResponse(Room r) {
        return new RoomResponse(
                r.getId(),
                r.getName(),
                r.getType(),
                r.getPasswordHash() != null,
                r.getCreatedBy(),
                r.getCreatedAt()
        );
    }
}
