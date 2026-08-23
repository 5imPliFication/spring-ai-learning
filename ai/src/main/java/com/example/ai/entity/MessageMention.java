package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "message_mentions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id", "mentioned_user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageMention {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(name = "mentioned_user_id", nullable = false, length = 36)
    private String mentionedUserId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
