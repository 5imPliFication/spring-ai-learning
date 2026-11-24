package com.example.ai.services;

import com.example.ai.entity.RoomMessage;
import com.example.ai.dto.CalendarActionCard;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.UserRepository;
import com.example.ai.tools.CalendarTools;
import com.example.ai.tools.ResilientToolCallback;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AzuraService {

    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final ChatClient chatClient;
    private final CalendarTools calendarTools;
    private final PendingCalendarActionService pendingCalendarActionService;

    private static final Pattern ACTION_TOKEN = Pattern.compile("AZURA_ACTION_([0-9a-zA-Z-]{36})");

    @Value("${app.ai.name}")
    private String aiName;

    @Value("${app.ai.system-prompt}")
    private String systemPromptConfig;

    @Value("${app.ai.max-tokens:256}")
    private Integer maxTokens;

    @Value("${app.ai.context-messages:20}")
    private Integer contextMessagesCount;

    public AzuraService(RoomMessageRepository roomMessageRepository,
                        UserRepository userRepository,
                        ChatClient chatClient,
                        CalendarTools calendarTools,
                        PendingCalendarActionService pendingCalendarActionService) {
        this.roomMessageRepository = roomMessageRepository;
        this.userRepository = userRepository;
        this.chatClient = chatClient;
        this.calendarTools = calendarTools;
        this.pendingCalendarActionService = pendingCalendarActionService;
    }

    /** One blocking model call; returns the complete reply. */
    public ChatResponse generateResponse(String roomId, String requesterId) {
        return buildPrompt(roomId, requesterId).call().chatResponse();
    }

    /** The same request, streamed. Tool calls still run between chunks. */
    public Flux<ChatResponse> streamResponse(String roomId, String requesterId) {
        return buildPrompt(roomId, requesterId).stream().chatResponse();
    }

    private ChatClient.ChatClientRequestSpec buildPrompt(String roomId, String requesterId) {
        List<RoomMessage> recent = new ArrayList<>(roomMessageRepository.findByRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                roomId, PageRequest.of(0, Math.max(1, contextMessagesCount))));
        Collections.reverse(recent); // chronological

        Set<String> senderIds = recent.stream().map(RoomMessage::getSenderId).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        userRepository.findAllById(senderIds).forEach(u -> names.put(u.getId(), u.getDisplayName()));
        names.putIfAbsent(AzuraReplyService.AI_SENDER_ID, aiName);

        String fullSystemPrompt = """
            Your name is %s. You are a participant in a group chat.
            %s
            %s
            You must respond casually and directly, like a friend in the chat. Keep it short and conversational.
            Do NOT use bullet points, numbered lists, or formal language.
            Just give the direct answer. Be helpful but brief.
            """.formatted(aiName, systemPromptConfig, currentDateTimeHint());

        String userPrompt = "Here's the recent chat:\n" + buildTranscript(recent, names)
                + "\nNow respond to the conversation above.";

        var prompt = chatClient.prompt()
                .system(fullSystemPrompt)
                .user(userPrompt)
                .options(DeepSeekChatOptions.builder().maxTokens(maxTokens));

        if (requesterId != null && !requesterId.isBlank()) {
            ToolCallback[] calendarCallbacks = Arrays.stream(ToolCallbacks.from(calendarTools))
                    .map(ResilientToolCallback::new)
                    .toArray(ToolCallback[]::new);
            prompt.tools(calendarCallbacks)
                    .toolContext(Map.of("userId", requesterId, "roomId", roomId));
        }
        return prompt;
    }

    /** "Name: text" per line, oldest first; media is summarised as [Image]/[File]/[Audio]. */
    static String buildTranscript(List<RoomMessage> chronological, Map<String, String> namesById) {
        StringBuilder sb = new StringBuilder();
        for (RoomMessage msg : chronological) {
            sb.append(namesById.getOrDefault(msg.getSenderId(), "Unknown")).append(": ");
            switch (msg.getMessageType() != null ? msg.getMessageType().toUpperCase() : "TEXT") {
                case "IMAGE" -> sb.append("[Image]");
                case "FILE" -> sb.append("[File]");
                case "AUDIO" -> sb.append("[Audio]");
                default -> sb.append(msg.getContent());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public RoomMessage saveAzuraMessage(String roomId, String content, ChatResponse chatResponse) {
        return saveAzuraMessage(roomId, content, chatResponse, null);
    }

    public RoomMessage saveAzuraMessage(String roomId, String content, ChatResponse chatResponse,
                                        CalendarActionCard actionCard) {
        Usage usage = chatResponse != null && chatResponse.getMetadata() != null
                ? chatResponse.getMetadata().getUsage() : null;
        return saveAzuraMessage(roomId, content, usage, actionCard);
    }

    public RoomMessage saveAzuraMessage(String roomId, String content, Usage usage, CalendarActionCard actionCard) {
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        if (usage != null) {
            promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
            completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
            totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : (promptTokens + completionTokens);
        }

        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId(AzuraReplyService.AI_SENDER_ID)
                .content(content == null ? "" : content)
                .messageType(actionCard != null ? "ACTION_CARD" : "TEXT")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .build();
        return roomMessageRepository.save(msg);
    }

    /**
     * Post-processes the raw model reply: when it embeds a staged-action token
     * (AZURA_ACTION_&lt;uuid&gt;), the token is stripped from the text and the resolved
     * card metadata is attached so the caller can persist an ACTION_CARD message.
     */
    public ProcessedAiResponse processActionToken(String rawContent, String userId) {
        if (rawContent == null) {
            return new ProcessedAiResponse(null, null);
        }
        Matcher matcher = ACTION_TOKEN.matcher(rawContent);
        if (!matcher.find()) {
            return new ProcessedAiResponse(rawContent, null);
        }
        String actionId = matcher.group(1);
        String cleaned = rawContent.replace(matcher.group(), "").trim();
        if (cleaned.isEmpty()) {
            cleaned = "A calendar action is waiting for your approval.";
        }
        return new ProcessedAiResponse(cleaned, pendingCalendarActionService.cardFor(actionId).orElse(null));
    }

    public record ProcessedAiResponse(String content, CalendarActionCard actionCard) {
    }

    private String currentDateTimeHint() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now(java.time.ZoneId.systemDefault());
        String date = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        String time = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm XXX"));
        return "Today's date is " + date + " and the current time is " + time
                + " (server timezone). When a user gives relative times like \"tomorrow\" or \"5pm\", "
                + "resolve them to concrete RFC3339 timestamps (e.g. 2026-09-14T17:00:00+07:00) using this date and time.";
    }
}
