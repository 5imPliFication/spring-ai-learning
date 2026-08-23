package com.example.ai.repository;

import com.example.ai.entity.RoomNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomNotificationSettingRepository extends JpaRepository<RoomNotificationSetting, Long> {
    Optional<RoomNotificationSetting> findByUserIdAndRoomId(String userId, String roomId);
    List<RoomNotificationSetting> findByUserId(String userId);

    @Query("""
           SELECT COUNT(m) FROM RoomMessage m
           WHERE m.roomId = :roomId
             AND m.deletedAt IS NULL
             AND m.senderId <> :userId
             AND m.createdAt > :since
           """)
    long countUnreadMessages(@Param("userId") String userId,
                             @Param("roomId") String roomId,
                             @Param("since") Instant since);
}
