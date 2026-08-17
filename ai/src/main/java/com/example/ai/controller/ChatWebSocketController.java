package com.example.ai.controller;

import com.example.ai.dto.ChatMessage;
import com.example.ai.entity.RoomMessage;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.UserRepository;
import com.example.ai.services.AzuraService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomMessageRepository roomMessageRepository;
    private final UserRepository userRepository;
    private final AzuraService azuraService;

    @Value("${app.ai.name}")
    private String aiName;

    @MessageMapping("/chat/{roomId}")
    public void handleMessage(@DestinationVariable String roomId, ChatMessage message) {
        String senderName = userRepository.findById(message.senderId())
                .map(u -> u.getDisplayName())
                .orElse("Unknown");

        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId(message.senderId())
                .content(message.content())
                .messageType("TEXT")
                .replyToId(message.replyToId())
                .build();
        roomMessageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                ChatMessage.chatReply(msg.getId(), message.senderId(), senderName, message.content(), message.replyToId()));

        if (message.content() != null && message.content().toLowerCase().contains("@ai")) {
            CompletableFuture.runAsync(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/room/" + roomId,
                            ChatMessage.typing("ai-bot", aiName));

                    ChatResponse chatResponse = azuraService.generateResponse(roomId);
                    String responseText = chatResponse != null && chatResponse.getResult() != null
                            ? chatResponse.getResult().getOutput().getText()
                            : "Sorry, I couldn't generate a response.";

                    RoomMessage aiMsg = azuraService.saveAzuraMessage(roomId, responseText, chatResponse);

                    messagingTemplate.convertAndSend("/topic/room/" + roomId,
                            ChatMessage.chat(aiMsg.getId(), "ai-bot", aiName, responseText));

                    messagingTemplate.convertAndSend("/topic/room/" + roomId,
                            ChatMessage.idle("ai-bot", aiName));
                } catch (Exception e) {
                    messagingTemplate.convertAndSend("/topic/room/" + roomId,
                            ChatMessage.idle("ai-bot", aiName));
                    e.printStackTrace();
                }
            });
        }
    }
}
