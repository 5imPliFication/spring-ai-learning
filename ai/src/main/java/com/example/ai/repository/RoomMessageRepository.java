package com.example.ai.repository;

import com.example.ai.entity.RoomMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, Long> {
    List<RoomMessage> findByRoomIdAndDeletedAtIsNullOrderByCreatedAtAsc(String roomId);
    List<RoomMessage> findTop20ByRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(String roomId);

    long countByCreatedAtGreaterThanEqualAndDeletedAtIsNull(Instant since);

    @Query("SELECT COALESCE(SUM(m.totalTokens), 0) FROM RoomMessage m WHERE m.createdAt >= :since AND m.deletedAt IS NULL")
    long sumTotalTokensSince(@Param("since") Instant since);
}
