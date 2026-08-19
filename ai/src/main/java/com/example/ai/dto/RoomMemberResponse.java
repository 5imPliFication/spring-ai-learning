package com.example.ai.dto;

import java.time.Instant;

public record RoomMemberResponse(
    String userId,
    String username,
    String displayName,
    String avatarUrl,
    String role,
    Instant joinedAt
) {}