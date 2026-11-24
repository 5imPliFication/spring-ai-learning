package com.example.ai.dto;

/**
 * A message for one user only, sent with {@code convertAndSendToUser(userId, QUEUE, notice)}.
 * Clients receive it by subscribing to {@code /user/queue/errors}.
 * Used for things like rate-limit and "AI busy" feedback that must not be broadcast to the room.
 */
public record UserNotice(String type, String roomId, String message, Long retryAfterSeconds) {

    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String AI_BUSY = "AI_BUSY";

    /** User-relative destination; the client-side subscription is {@code /user} + this. */
    public static final String QUEUE = "/queue/errors";
}
