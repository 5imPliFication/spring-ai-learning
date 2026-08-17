package com.example.ai.services;

import com.example.ai.dto.FriendResponse;
import com.example.ai.dto.RoomResponse;
import com.example.ai.entity.Friend;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMember;
import com.example.ai.entity.User;
import com.example.ai.repository.FriendRepository;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.RoomRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final NotificationService notificationService;

    public void sendFriendRequest(User user, String friendId) {
        if (user.getId().equals(friendId)) {
            throw new IllegalArgumentException("Cannot add yourself as a friend");
        }
        userRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Friend user not found"));

        if (friendRepository.findByUserIdAndFriendId(user.getId(), friendId).isPresent()) {
            throw new IllegalArgumentException("Friend request already sent or exists");
        }

        Friend request = Friend.builder()
                .userId(user.getId())
                .friendId(friendId)
                .status("PENDING")
                .build();
        friendRepository.save(request);

        notificationService.create(
                friendId,
                "FRIEND_REQUEST",
                user.getDisplayName() + " sent you a friend request",
                "Tap here to view and accept the request",
                user.getId(),
                null
        );
    }

    public void acceptFriendRequest(User user, Long friendshipId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friend.getFriendId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized to accept this request");
        }

        friend.setStatus("ACCEPTED");
        friendRepository.save(friend);

        // Save reciprocal entry for easy lookup
        if (friendRepository.findByUserIdAndFriendId(user.getId(), friend.getUserId()).isEmpty()) {
            Friend reciprocal = Friend.builder()
                    .userId(user.getId())
                    .friendId(friend.getUserId())
                    .status("ACCEPTED")
                    .build();
            friendRepository.save(reciprocal);
        }

        notificationService.create(
                friend.getUserId(),
                "FRIEND_ACCEPTED",
                user.getDisplayName() + " accepted your friend request",
                "You are now friends — say hi!",
                user.getId(),
                null
        );
    }

    public void declineFriendRequest(User user, Long friendshipId) {
        Friend friend = friendRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found"));

        if (!friend.getFriendId().equals(user.getId())) {
            throw new IllegalArgumentException("Unauthorized to decline this request");
        }
        if (!"PENDING".equals(friend.getStatus())) {
            throw new IllegalArgumentException("Friend request is no longer pending");
        }

        friendRepository.delete(friend);
    }

    public List<FriendResponse> getPendingRequests(User user) {
        List<Friend> list = friendRepository.findByFriendIdAndStatus(user.getId(), "PENDING");
        List<FriendResponse> responses = new ArrayList<>();

        for (Friend f : list) {
            userRepository.findById(f.getUserId()).ifPresent(sender -> {
                responses.add(new FriendResponse(
                        f.getId(),
                        sender.getId(),
                        sender.getUsername(),
                        sender.getDisplayName(),
                        sender.getAvatarUrl(),
                        f.getStatus(),
                        f.getCreatedAt()
                ));
            });
        }
        return responses;
    }

    public List<FriendResponse> getFriends(User user) {
        List<Friend> list = friendRepository.findByUserIdAndStatus(user.getId(), "ACCEPTED");
        List<FriendResponse> responses = new ArrayList<>();

        for (Friend f : list) {
            userRepository.findById(f.getFriendId()).ifPresent(friendUser -> {
                responses.add(new FriendResponse(
                        f.getId(),
                        friendUser.getId(),
                        friendUser.getUsername(),
                        friendUser.getDisplayName(),
                        friendUser.getAvatarUrl(),
                        f.getStatus(),
                        f.getCreatedAt()
                ));
            });
        }
        return responses;
    }

    public RoomResponse createOrGetDirectMessageRoom(User user, String friendId) {
        User friend = userRepository.findById(friendId)
                .orElseThrow(() -> new IllegalArgumentException("Friend user not found"));

        // Check if DM room already exists between these 2 users
        Room existing = findDirectRoom(user.getId(), friendId).orElse(null);
        if (existing != null) {
            return toRoomResponse(existing);
        }

        // Create new DM room
        String roomName = "DM: " + user.getDisplayName() + " & " + friend.getDisplayName();
        Room dmRoom = Room.builder()
                .id(UUID.randomUUID().toString())
                .name(roomName)
                .type("DIRECT")
                .createdBy(user.getId())
                .build();
        roomRepository.save(dmRoom);

        // Add both members
        roomMemberRepository.save(RoomMember.builder().roomId(dmRoom.getId()).userId(user.getId()).role("OWNER").build());
        roomMemberRepository.save(RoomMember.builder().roomId(dmRoom.getId()).userId(friendId).role("MEMBER").build());

        return toRoomResponse(dmRoom);
    }

    @Transactional
    public void unfriend(User user, String friendId) {
        Optional<Friend> forward = friendRepository.findByUserIdAndFriendId(user.getId(), friendId);
        Optional<Friend> reverse = friendRepository.findByUserIdAndFriendId(friendId, user.getId());

        if (forward.isEmpty() && reverse.isEmpty()) {
            throw new IllegalArgumentException("You are not friends with this user");
        }

        forward.ifPresent(friendRepository::delete);
        reverse.ifPresent(friendRepository::delete);

        // Erase the entire DM chat between the two users
        findDirectRoom(user.getId(), friendId).ifPresent(dmRoom -> {
            roomMessageRepository.deleteByRoomId(dmRoom.getId());
            roomMemberRepository.deleteByRoomId(dmRoom.getId());
            roomRepository.delete(dmRoom);
        });
    }

    private Optional<Room> findDirectRoom(String userId, String friendId) {
        List<RoomMember> userRooms = roomMemberRepository.findByUserId(userId);
        for (RoomMember rm : userRooms) {
            Room room = roomRepository.findById(rm.getRoomId()).orElse(null);
            if (room != null && "DIRECT".equalsIgnoreCase(room.getType())
                    && roomMemberRepository.existsByRoomIdAndUserId(room.getId(), friendId)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    private RoomResponse toRoomResponse(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getType(),
                room.getPasswordHash() != null,
                room.getCreatedBy(),
                room.getCreatedAt(),
                room.isPrivate()
        );
    }
}
