package com.example.ai.dto;

import java.time.Instant;
import java.util.List;

public record ChatMessage(
    String senderId,
    String senderName,
    String content,
    String messageType,  // TEXT, IMAGE, FILE, AUDIO, ACTION_CARD
    String mediaUrl,
    String type,         // CHAT, TYPING, IDLE, DELETE, STREAM_START, STREAM_DELTA, STREAM_ERROR
    Instant timestamp,
    Long replyToId,
    Long messageId,
    List<String> mentionedUserIds,
    CalendarActionCard actionCard,
    // Set on AI streaming events and on the final CHAT that replaces the streamed bubble.
    String streamId
) {
    public static ChatMessage chat(Long messageId, String senderId, String senderName, String content) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), null, messageId, null, null, null);
    }

    public static ChatMessage chatReply(Long messageId, String senderId, String senderName, String content, Long replyToId) {
        return new ChatMessage(senderId, senderName, content, "TEXT", null, "CHAT", Instant.now(), replyToId, messageId, null, null, null);
    }

    public static ChatMessage chatMedia(Long messageId, String senderId, String senderName, String content,
                                        String messageType, String mediaUrl, Long replyToId) {
        return chatMedia(messageId, senderId, senderName, content, messageType, mediaUrl, replyToId, null);
    }

    public static ChatMessage chatMedia(Long messageId, String senderId, String senderName, String content,
                                        String messageType, String mediaUrl, Long replyToId,
                                        List<String> mentionedUserIds) {
        return new ChatMessage(senderId, senderName, content, messageType, mediaUrl, "CHAT", Instant.now(),
                replyToId, messageId, mentionedUserIds, null, null);
    }

    public static ChatMessage chatActionCard(Long messageId, String senderId, String senderName,
                                             String content, CalendarActionCard actionCard) {
        return new ChatMessage(senderId, senderName, content, "ACTION_CARD", null, "CHAT", Instant.now(),
                null, messageId, null, actionCard, null);
    }

    public static ChatMessage typing(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "TYPING", Instant.now(), null, null, null, null, null);
    }

    public static ChatMessage idle(String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "IDLE", Instant.now(), null, null, null, null, null);
    }

    public static ChatMessage delete(Long messageId) {
        return new ChatMessage(null, null, null, null, null, "DELETE", Instant.now(), null, messageId, null, null, null);
    }

    /** Final AI message replacing the streamed bubble {@code streamId}. */
    public ChatMessage withStreamId(String streamId) {
        return new ChatMessage(senderId, senderName, content, messageType, mediaUrl, type, timestamp,
                replyToId, messageId, mentionedUserIds, actionCard, streamId);
    }

    /** An AI reply is starting; clients add an empty streaming bubble keyed by {@code streamId}. */
    public static ChatMessage streamStart(String streamId, String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, "", "TEXT", null, "STREAM_START", Instant.now(),
                null, null, null, null, streamId);
    }

    /**
     * New text for the streaming bubble; {@code content} is only what arrived since the previous delta.
     * The final CHAT with the same streamId (the stream's end) carries the complete saved text.
     */
    public static ChatMessage streamDelta(String streamId, String senderId, String senderName, String newText) {
        return new ChatMessage(senderId, senderName, newText, "TEXT", null, "STREAM_DELTA", Instant.now(),
                null, null, null, null, streamId);
    }

    /** The stream failed; clients drop the partial bubble. */
    public static ChatMessage streamError(String streamId, String senderId, String senderName) {
        return new ChatMessage(senderId, senderName, null, null, null, "STREAM_ERROR", Instant.now(),
                null, null, null, null, streamId);
    }
}
