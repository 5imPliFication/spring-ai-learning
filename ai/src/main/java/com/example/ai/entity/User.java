package com.example.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String role;

    // Profile fields
    @Column(length = 500)
    private String bio;

    @Column(length = 100)
    private String location;

    @Column(length = 50)
    private String gender;

    @Column(length = 20)
    private String phone;

    // Visibility toggles
    @Column(name = "show_bio", nullable = false)
    private Boolean showBio;

    @Column(name = "show_location", nullable = false)
    private Boolean showLocation;

    @Column(name = "show_gender", nullable = false)
    private Boolean showGender;

    @Column(name = "show_phone", nullable = false)
    private Boolean showPhone;

    @Column(name = "show_links", nullable = false)
    private Boolean showLinks;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "userId", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<UserLink> links = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (role == null) role = "USER";
        if (lastActiveAt == null) lastActiveAt = Instant.now();
        if (showBio == null) showBio = true;
        if (showLocation == null) showLocation = true;
        if (showGender == null) showGender = true;
        if (showPhone == null) showPhone = true;
        if (showLinks == null) showLinks = true;
    }
}
