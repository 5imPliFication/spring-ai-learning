package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (position == null) position = 0;
    }
}
