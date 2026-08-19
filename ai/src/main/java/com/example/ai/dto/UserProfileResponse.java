package com.example.ai.dto;

import java.time.Instant;

public record UserProfileResponse(
    String id,
    String username,
    String displayName,
    String avatarUrl,
    String role,
    Instant createdAt,
    Instant lastActiveAt
) {}
