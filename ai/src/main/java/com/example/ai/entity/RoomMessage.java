package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "room_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private String roomId;

    @Column(name = "sender_id", nullable = false)
    private String senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (messageType == null) messageType = "TEXT";
        if (promptTokens == null) promptTokens = 0;
        if (completionTokens == null) completionTokens = 0;
        if (totalTokens == null) totalTokens = 0;
    }
}
