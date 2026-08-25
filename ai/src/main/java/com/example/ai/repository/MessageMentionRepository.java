package com.example.ai.repository;

import com.example.ai.entity.MessageMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface MessageMentionRepository extends JpaRepository<MessageMention, Long> {

    boolean existsByMessageIdAndMentionedUserId(Long messageId, String mentionedUserId);

    @Query("SELECT m.mentionedUserId FROM MessageMention m WHERE m.messageId = :messageId")
    Set<String> findMentionedUserIdsByMessageId(@Param("messageId") Long messageId);

    List<MessageMention> findByMessageIdIn(List<Long> messageIds);

    @Query("""
           SELECT COUNT(DISTINCT mm.messageId) FROM MessageMention mm
           JOIN RoomMessage rm ON rm.id = mm.messageId
           WHERE mm.mentionedUserId = :userId
             AND mm.roomId = :roomId
             AND mm.createdAt > :since
             AND rm.deletedAt IS NULL
             AND rm.senderId <> :userId
           """)
    long countUnreadMentions(@Param("userId") String userId,
                             @Param("roomId") String roomId,
                             @Param("since") Instant since);
}
