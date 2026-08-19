package com.example.ai.services;

import com.example.ai.dto.AdminDashboardStats;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.RoomRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMessageRepository roomMessageRepository;

    public AdminDashboardStats getDashboardStats() {
        Instant now = Instant.now();
        Instant last24h = now.minus(24, ChronoUnit.HOURS);
        Instant last7d = now.minus(7, ChronoUnit.DAYS);
        Instant last30d = now.minus(30, ChronoUnit.DAYS);
        Instant last5m = now.minus(5, ChronoUnit.MINUTES);

        long dailyMessages = roomMessageRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(last24h);
        long weeklyMessages = roomMessageRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(last7d);
        long monthlyMessages = roomMessageRepository.countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(last30d);

        long activeUsersCount = userRepository.countByLastActiveAtGreaterThanEqualAndDeletedAtIsNull(last7d);
        long onlineUsersCount = userRepository.countByLastActiveAtGreaterThanEqualAndDeletedAtIsNull(last5m);

        long totalRoomsCount = roomRepository.countByDeletedAtIsNull();

        long dailyTokensUsed = roomMessageRepository.sumTotalTokensSince(last24h);
        long weeklyTokensUsed = roomMessageRepository.sumTotalTokensSince(last7d);
        long monthlyTokensUsed = roomMessageRepository.sumTotalTokensSince(last30d);

        return new AdminDashboardStats(
                dailyMessages,
                weeklyMessages,
                monthlyMessages,
                activeUsersCount,
                onlineUsersCount,
                totalRoomsCount,
                dailyTokensUsed,
                weeklyTokensUsed,
                monthlyTokensUsed
        );
    }

    // User Management
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(String userId, boolean hardDelete) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (hardDelete) {
            userRepository.delete(user);
        } else {
            user.setDeletedAt(Instant.now());
            userRepository.save(user);
        }
    }

    // Room Management
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public void deleteRoom(String roomId, boolean hardDelete) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (hardDelete) {
            roomRepository.delete(room);
        } else {
            room.setDeletedAt(Instant.now());
            roomRepository.save(room);
        }
    }

    // Message Management
    public List<RoomMessage> getAllMessages() {
        return roomMessageRepository.findAll();
    }

    public void deleteMessage(Long messageId, boolean hardDelete) {
        RoomMessage msg = roomMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (hardDelete) {
            roomMessageRepository.delete(msg);
        } else {
            msg.setDeletedAt(Instant.now());
            roomMessageRepository.save(msg);
        }
    }
}
