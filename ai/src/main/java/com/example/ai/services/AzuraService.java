package com.example.ai.services;

import com.example.ai.entity.RoomMessage;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.UserRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AzuraService {

    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final ChatClient chatClient;

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
                        ChatClient chatClient) {
        this.roomMessageRepository = roomMessageRepository;
        this.userRepository = userRepository;
        this.chatClient = chatClient;
    }

    public ChatResponse generateResponse(String roomId) {
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
            You must respond casually and directly, like a friend in the chat. Keep it short and conversational.
            Do NOT use bullet points, numbered lists, or formal language.
            Just give the direct answer. Be helpful but brief.
            """.formatted(aiName, systemPromptConfig);

        String userPrompt = "Here's the recent chat:\n" + contextBuilder + "\nNow respond to the conversation above.";

        return chatClient.prompt()
                .system(fullSystemPrompt)
                .user(userPrompt)
                .options(DeepSeekChatOptions.builder().maxTokens(maxTokens))
                .call()
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
}
