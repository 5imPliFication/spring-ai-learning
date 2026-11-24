package com.example.ai.services;

import com.example.ai.config.AiExecutorConfig;
import com.example.ai.config.RoomPresenceTracker;
import com.example.ai.dto.ChatMessage;
import com.example.ai.dto.UserNotice;
import com.example.ai.entity.RoomMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Handles "@ai" requests from chat: rate limiting, running the model call on the bounded AI pool,
 * broadcasting the reply to the room, and notifying the requester if they're not looking.
 */
@Slf4j
@Service
public class AzuraReplyService {

    public static final String AI_SENDER_ID = "ai-bot";

    /** "@ai" as its own word: matches "@ai hi", "hey @AI?" and "(@ai)", not "@aiden", "@ai-bot" or "me@ai.com". */
    private static final Pattern AI_MENTION = Pattern.compile("(?i)(?<![\\w@.])@ai(?![\\w.@-]*\\w)");

    private final SimpMessagingTemplate messagingTemplate;
    private final AzuraService azuraService;
    private final RateLimitService rateLimitService;
    private final TaskExecutor aiExecutor;
    private final RoomPresenceTracker roomPresenceTracker;
    private final RoomNotificationSettingsService notificationSettingsService;
    private final NotificationService notificationService;

    /** Send an update at least this often while text is arriving... */
    private static final Duration FLUSH_INTERVAL = Duration.ofMillis(75);
    /** ...or as soon as this many new characters are ready. */
    private static final int FLUSH_CHARS = 40;

    private final long flushIntervalNanos = FLUSH_INTERVAL.toNanos();

    @Value("${app.ai.name}")
    private String aiName;

    @Value("${app.ai.streaming.enabled:true}")
    private boolean streamingEnabled;

    @Value("${app.ai.streaming.timeout:90s}")
    private Duration streamTimeout = Duration.ofSeconds(90);

    public AzuraReplyService(SimpMessagingTemplate messagingTemplate,
                             AzuraService azuraService,
                             RateLimitService rateLimitService,
                             @Qualifier(AiExecutorConfig.AI_EXECUTOR) TaskExecutor aiExecutor,
                             RoomPresenceTracker roomPresenceTracker,
                             RoomNotificationSettingsService notificationSettingsService,
                             NotificationService notificationService) {
        this.messagingTemplate = messagingTemplate;
        this.azuraService = azuraService;
        this.rateLimitService = rateLimitService;
        this.aiExecutor = aiExecutor;
        this.roomPresenceTracker = roomPresenceTracker;
        this.notificationSettingsService = notificationSettingsService;
        this.notificationService = notificationService;
    }

    public static boolean mentionsAi(String content) {
        return content != null && AI_MENTION.matcher(content).find();
    }

    /**
     * Queues an AI reply for this room. Returns immediately; the reply arrives over the room topic.
     * The requester gets a private notice instead if they're rate limited or the AI pool is full.
     */
    public void requestReply(String roomId, String requesterId, String roomName) {
        RateLimitService.Decision decision = rateLimitService.tryConsumeAi(requesterId);
        if (!decision.allowed()) {
            sendNotice(requesterId, new UserNotice(UserNotice.RATE_LIMITED, roomId,
                    "You're asking " + aiName + " too often. Try again in "
                            + decision.retryAfterSeconds() + "s.",
                    decision.retryAfterSeconds()));
            return;
        }

        try {
            aiExecutor.execute(() -> generateAndBroadcast(roomId, requesterId, roomName));
        } catch (TaskRejectedException e) {
            log.warn("AI executor full; rejecting request in room {}", roomId);
            sendNotice(requesterId, new UserNotice(UserNotice.AI_BUSY, roomId,
                    aiName + " is busy right now. Please try again in a moment.", null));
        }
    }

    void generateAndBroadcast(String roomId, String requesterId, String roomName) {
        String topic = "/topic/room/" + roomId;
        try {
            messagingTemplate.convertAndSend(topic, ChatMessage.typing(AI_SENDER_ID, aiName));
            if (streamingEnabled) {
                streamAndBroadcast(topic, roomId, requesterId, roomName);
            } else {
                callAndBroadcast(topic, roomId, requesterId, roomName);
            }
        } catch (Exception e) {
            log.error("AI reply failed in room {}", roomId, e);
        } finally {
            messagingTemplate.convertAndSend(topic, ChatMessage.idle(AI_SENDER_ID, aiName));
        }
    }

    private void callAndBroadcast(String topic, String roomId, String requesterId, String roomName) {
        ChatResponse chatResponse = azuraService.generateResponse(roomId, requesterId);
        String responseText = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput().getText()
                : null;
        Usage usage = chatResponse != null && chatResponse.getMetadata() != null
                ? chatResponse.getMetadata().getUsage() : null;
        publishFinal(topic, roomId, requesterId, roomName, responseText, usage, null);
    }

    /**
     * Streams the reply as STREAM_START, then throttled STREAM_DELTA events (new text only), then saves it and
     * sends the final CHAT with the same streamId, which ends the stream: clients swap the bubble for the saved message.
     * Runs on the AI executor thread, so blocking on the Flux here is intended.
     */
    private void streamAndBroadcast(String topic, String roomId, String requesterId, String roomName) {
        String streamId = UUID.randomUUID().toString();
        StreamState state = new StreamState();

        try {
            // doOnNext handles every chunk in order before an error surfaces (toIterable would drop buffered ones).
            // Reactive Streams delivers onNext serially, and blockLast() gives us a happens-before on return.
            azuraService.streamResponse(roomId, requesterId)
                    .timeout(streamTimeout)
                    .doOnNext(chunk -> onChunk(topic, streamId, state, chunk))
                    .blockLast(streamTimeout.plusSeconds(5));
            flush(topic, streamId, state, true); // whatever the throttle held back
            publishFinal(topic, roomId, requesterId, roomName, state.text.raw(), state.usage, streamId);
        } catch (RuntimeException e) {
            // Clients only drop a bubble that is still streaming, so this is harmless if the final CHAT went out.
            // Every client in the room shows a toast for this event.
            messagingTemplate.convertAndSend(topic, ChatMessage.streamError(streamId, AI_SENDER_ID, aiName));
            throw e;
        }
    }

    private static final class StreamState {
        final StreamingText text = new StreamingText();
        Usage usage;
        boolean started;
        /** Visible text already sent as deltas. */
        String sent = "";
        long lastSentAt;
    }

    private void onChunk(String topic, String streamId, StreamState state, ChatResponse chunk) {
        if (chunk == null) return;
        Usage chunkUsage = chunk.getMetadata() != null ? chunk.getMetadata().getUsage() : null;
        if (chunkUsage != null && chunkUsage.getTotalTokens() != null && chunkUsage.getTotalTokens() > 0) {
            state.usage = chunkUsage; // the final model call's usage arrives on its last chunk
        }
        if (chunk.getResult() == null || chunk.getResult().getOutput() == null) return;
        state.text.append(chunk.getResult().getOutput().getText());

        flush(topic, streamId, state, false);
    }

    private void flush(String topic, String streamId, StreamState state, boolean force) {
        String visible = state.text.visible();
        // Visible text only ever grows at the end (held-back marker text is never sent), so the new part is a suffix.
        // If that ever fails to hold, skip deltas: the final CHAT carries the full text anyway.
        if (!visible.startsWith(state.sent) || visible.length() == state.sent.length()) return;

        long now = System.nanoTime();
        boolean dueByTime = now - state.lastSentAt >= flushIntervalNanos;
        boolean dueBySize = visible.length() - state.sent.length() >= FLUSH_CHARS;
        if (visible.isBlank() || !(force || dueByTime || dueBySize)) return;

        if (!state.started) {
            messagingTemplate.convertAndSend(topic, ChatMessage.streamStart(streamId, AI_SENDER_ID, aiName));
            state.started = true;
        }
        messagingTemplate.convertAndSend(topic,
                ChatMessage.streamDelta(streamId, AI_SENDER_ID, aiName, visible.substring(state.sent.length())));
        state.sent = visible;
        state.lastSentAt = now;
    }

    private void publishFinal(String topic, String roomId, String requesterId, String roomName,
                              String rawText, Usage usage, String streamId) {
        AzuraService.ProcessedAiResponse processed = azuraService.processActionToken(rawText, requesterId);
        String content = finalContent(processed);
        RoomMessage aiMsg = azuraService.saveAzuraMessage(roomId, content, usage, processed.actionCard());

        ChatMessage message = processed.actionCard() != null
                ? ChatMessage.chatActionCard(aiMsg.getId(), AI_SENDER_ID, aiName, content, processed.actionCard())
                : ChatMessage.chat(aiMsg.getId(), AI_SENDER_ID, aiName, content);
        messagingTemplate.convertAndSend(topic, streamId != null ? message.withStreamId(streamId) : message);

        try {
            notifyRequesterIfAway(roomId, requesterId, roomName, content);
        } catch (RuntimeException e) {
            log.warn("Could not notify {} about AI reply in room {}", requesterId, roomId, e);
        }
    }

    static String finalContent(AzuraService.ProcessedAiResponse processed) {
        String content = processed.content();
        if (content == null || content.isBlank()) {
            return processed.actionCard() != null
                    ? "A calendar action is waiting for your approval."
                    : "Sorry, I couldn't generate a response. Please try again.";
        }
        return content;
    }

    private void notifyRequesterIfAway(String roomId, String requesterId, String roomName, String content) {
        if (requesterId == null || requesterId.isBlank()
                || roomPresenceTracker.isViewing(requesterId, roomId)
                || !notificationSettingsService.shouldNotify(requesterId, roomId, false)) {
            return;
        }
        String snippet = content.length() > 120 ? content.substring(0, 120) + "…" : content;
        notificationService.create(
                requesterId,
                "AI_REPLY",
                aiName + " replied" + (roomName != null ? " in " + roomName : ""),
                snippet,
                AI_SENDER_ID,
                roomId
        );
    }

    private void sendNotice(String userId, UserNotice notice) {
        messagingTemplate.convertAndSendToUser(userId, UserNotice.QUEUE, notice);
    }
}
