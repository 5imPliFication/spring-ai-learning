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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public ChatResponse generateResponse(String roomId, String requesterId) {
        List<RoomMessage> topMessages = new ArrayList<>(
                roomMessageRepository.findTop20ByRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(roomId)
        );

        if (topMessages.size() > contextMessagesCount) {
            topMessages = topMessages.subList(0, contextMessagesCount);
        }

        // Reverse to chronological order
        java.util.Collections.reverse(topMessages);

        Map<String, String> userNames = new HashMap<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (RoomMessage msg : topMessages) {
            String senderName = userNames.computeIfAbsent(msg.getSenderId(), id ->
                userRepository.findById(id).map(u -> u.getDisplayName()).orElse("Unknown")
            );

            contextBuilder.append(senderName).append(": ");
            switch (msg.getMessageType() != null ? msg.getMessageType().toUpperCase() : "TEXT") {
                case "IMAGE" -> contextBuilder.append("[Image]");
                case "FILE" -> contextBuilder.append("[File]");
                case "AUDIO" -> contextBuilder.append("[Audio]");
                default -> contextBuilder.append(msg.getContent());
            }
            contextBuilder.append("\n");
        }

        String fullSystemPrompt = """
            Your name is %s. You are a participant in a group chat.
            %s
            %s
            You must respond casually and directly, like a friend in the chat. Keep it short and conversational.
            Do NOT use bullet points, numbered lists, or formal language.
            Just give the direct answer. Be helpful but brief.
            """.formatted(aiName, systemPromptConfig, currentDateTimeHint());

        String userPrompt = "Here's the recent chat:\n" + contextBuilder + "\nNow respond to the conversation above.";

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

        return prompt.call()
                .chatResponse();
    }

    public RoomMessage saveAzuraMessage(String roomId, String content, ChatResponse chatResponse) {
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        if (chatResponse != null && chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();
            promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
            completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
            totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens().intValue() : (promptTokens + completionTokens);
        }

        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId("ai-bot")
                .content(content)
                .messageType("TEXT")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .build();
        return roomMessageRepository.save(msg);
    }

    public RoomMessage saveAzuraMessage(String roomId, String content, ChatResponse chatResponse,
                                        CalendarActionCard actionCard) {
        if (actionCard == null) {
            return saveAzuraMessage(roomId, content, chatResponse);
        }
        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId("ai-bot")
                .content(content == null ? "" : content)
                .messageType("ACTION_CARD")
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
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
