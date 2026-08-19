package com.example.ai.dto;

import java.time.Instant;

public record FriendResponse(
    Long id,
    String friendId,
    String friendUsername,
    String friendDisplayName,
    String friendAvatarUrl,
    String status,
    Instant createdAt
) {}
