package com.example.ai.dto;

import java.time.Instant;

public record ChatMessage(
    String senderId,
    String senderName,
    String content,
    String messageType,  // TEXT, IMAGE, FILE, AUDIO
    String mediaUrl,
    String type,         // CHAT, TYPING, IDLE
    Instant timestamp
) {
    public static ChatMessage chat(String senderId, String senderName, String content) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now());
    }

    public static ChatMessage typing(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "TYPING", Instant.now());
    }

    public static ChatMessage idle(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "IDLE", Instant.now());
    }
}
