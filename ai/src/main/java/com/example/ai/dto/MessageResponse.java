package com.example.ai.dto;

import java.time.Instant;

public record MessageResponse(
    Long id,
    String senderId,
    String senderName,
    String content,
    String messageType,
    String mediaUrl,
    Instant createdAt
) {}
