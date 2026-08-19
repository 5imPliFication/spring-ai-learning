package com.example.ai.dto;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String type,
        String title,
        String body,
        String senderId,
        String senderName,
        String roomId,
        boolean read,
        Instant createdAt
) {}