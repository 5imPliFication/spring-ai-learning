package com.example.ai.dto;

import java.time.Instant;
import java.util.List;

public record ChatMessage(
    String senderId,
    String senderName,
    String content,
    String messageType,  // TEXT, IMAGE, FILE, AUDIO
    String mediaUrl,
    String type,         // CHAT, TYPING, IDLE, DELETE
    Instant timestamp,
    Long replyToId,
    Long messageId,
    List<String> mentionedUserIds
) {
    public static ChatMessage chat(Long messageId, String senderId, String senderName, String content) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), null, messageId, null);
    }

    public static ChatMessage chatReply(Long messageId, String senderId, String senderName, String content, Long replyToId) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), replyToId, messageId, null);
    }

    public static ChatMessage chatMedia(Long messageId, String senderId, String senderName, String content,
                                        String messageType, String mediaUrl, Long replyToId) {
        return chatMedia(messageId, senderId, senderName, content, messageType, mediaUrl, replyToId, null);
    }

    public static ChatMessage chatMedia(Long messageId, String senderId, String senderName, String content,
                                        String messageType, String mediaUrl, Long replyToId,
                                        List<String> mentionedUserIds) {
        return new ChatMessage(senderId, senderName, content, messageType, mediaUrl, "CHAT", Instant.now(),
                replyToId, messageId, mentionedUserIds);
    }

    public static ChatMessage typing(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "TYPING", Instant.now(), null, null, null);
    }

    public static ChatMessage idle(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "IDLE", Instant.now(), null, null, null);
    }

    public static ChatMessage delete(Long messageId) {
        return new ChatMessage(null, null, null, null, null, "DELETE", Instant.now(), null, messageId, null);
    }
}
