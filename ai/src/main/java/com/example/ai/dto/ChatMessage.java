package com.example.ai.dto;

import java.time.Instant;

public record ChatMessage(
    String senderId,
    String senderName,
    String content,
    String messageType,  // TEXT, IMAGE, FILE, AUDIO
    String mediaUrl,
    String type,         // CHAT, TYPING, IDLE, DELETE
    Instant timestamp,
    Long replyToId,
    Long messageId
) {
    public static ChatMessage chat(Long messageId, String senderId, String senderName, String content) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), null, messageId);
    }

    public static ChatMessage chatReply(Long messageId, String senderId, String senderName, String content, Long replyToId) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), replyToId, messageId);
    }

    public static ChatMessage typing(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "TYPING", Instant.now(), null, null);
    }

    public static ChatMessage idle(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "IDLE", Instant.now(), null, null);
    }

    public static ChatMessage delete(Long messageId) {
        return new ChatMessage(null, null, null, null, null, "DELETE", Instant.now(), null, messageId);
    }
}