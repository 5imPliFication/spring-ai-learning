package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "room_notification_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "room_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomNotificationSetting {
    public static final String MODE_ALL = "ALL";
    public static final String MODE_MENTIONS_ONLY = "MENTIONS_ONLY";
    public static final String MODE_MUTED = "MUTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "room_id", nullable = false, length = 36)
    private String roomId;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (mode == null) mode = MODE_ALL;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
