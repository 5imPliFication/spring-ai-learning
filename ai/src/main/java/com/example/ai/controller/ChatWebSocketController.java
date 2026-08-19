package com.example.ai.controller;

import com.example.ai.config.RoomPresenceTracker;
import com.example.ai.dto.ChatMessage;
import com.example.ai.entity.Room;
import com.example.ai.entity.RoomMember;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.RoomRepository;
import com.example.ai.repository.UserRepository;
import com.example.ai.services.AzuraService;
import com.example.ai.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final AzuraService azuraService;
    private final NotificationService notificationService;
    private final RoomPresenceTracker roomPresenceTracker;

    @Value("${app.ai.name}")
    private String aiName;

    @MessageMapping("/chat/{roomId}")
    public void handleMessage(@DestinationVariable String roomId, Principal principal, ChatMessage message) {
        String senderId = principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user
                ? user.getId()
                : null;
        if (senderId == null) {
            throw new MessageDeliveryException("Unauthenticated sender");
        }

        String senderName = userRepository.findById(senderId)
                .map(u -> u.getDisplayName())
                .orElse("Unknown");

        String messageType = message.messageType() != null && !message.messageType().isBlank()
                ? message.messageType().toUpperCase()
                : "TEXT";

        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(message.content())
                .messageType(messageType)
                .mediaUrl(message.mediaUrl())
                .replyToId(message.replyToId())
                .build();
        roomMessageRepository.save(msg);

        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                ChatMessage.chatMedia(msg.getId(), senderId, senderName, message.content(),
                        messageType, message.mediaUrl(), message.replyToId()));

        Room room = roomRepository.findById(roomId).orElse(null);

        if (room != null && "DIRECT".equalsIgnoreCase(room.getType())) {
            roomMemberRepository.findByRoomId(roomId).stream()
                    .map(RoomMember::getUserId)
                    .filter(recipientId -> !recipientId.equals(senderId))
                    .findFirst()
                    .ifPresent(recipientId -> {
                        if (!roomPresenceTracker.isViewing(recipientId, roomId)) {
                            notificationService.create(
                                    recipientId,
                                    "DM_MESSAGE",
                                    senderName,
                                    message.content() != null ? message.content() : "New direct message",
                                    senderId,
                                    roomId
                            );
                        }
                    });
        }

        if (message.content() != null && message.content().toLowerCase().contains("@ai")) {
            String requesterId = senderId;
            String roomName = room != null ? room.getName() : null;
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

                    if (requesterId != null && !requesterId.isBlank()
                            && !roomPresenceTracker.isViewing(requesterId, roomId)) {
                        String snippet = responseText.length() > 120
                                ? responseText.substring(0, 120) + "…"
                                : responseText;
                        notificationService.create(
                                requesterId,
                                "AI_REPLY",
                                aiName + " replied" + (roomName != null ? " in " + roomName : ""),
                                snippet,
                                "ai-bot",
                                roomId
                        );
                    }
                } catch (Exception e) {
                    messagingTemplate.convertAndSend("/topic/room/" + roomId,
                            ChatMessage.idle("ai-bot", aiName));
                    e.printStackTrace();
                }
            });
        }
    }
}
