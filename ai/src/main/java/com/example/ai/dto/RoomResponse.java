package com.example.ai.dto;

import java.time.Instant;

public record RoomResponse(
    String id,
    String name,
    String type,
    boolean isProtected,
    String createdBy,
    Instant createdAt,
    boolean isPrivate
) {}
