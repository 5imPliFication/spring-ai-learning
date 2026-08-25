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
import com.example.ai.services.MentionService;
import com.example.ai.services.NotificationService;
import com.example.ai.services.RoomNotificationSettingsService;
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
import java.util.List;
import java.util.Set;
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
    private final MentionService mentionService;
    private final RoomNotificationSettingsService notificationSettingsService;

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

        Set<String> mentionedUserIds = "TEXT".equals(messageType)
                ? mentionService.extractMentionedUserIds(roomId, senderId, message.content())
                : Set.of();

        RoomMessage msg = RoomMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(message.content())
                .messageType(messageType)
                .mediaUrl(message.mediaUrl())
                .replyToId(message.replyToId())
                .build();
        roomMessageRepository.save(msg);
        mentionService.saveMentions(msg, mentionedUserIds);

        messagingTemplate.convertAndSend("/topic/room/" + roomId,
                ChatMessage.chatMedia(msg.getId(), senderId, senderName, message.content(),
                        messageType, message.mediaUrl(), message.replyToId(),
                        List.copyOf(mentionedUserIds)));

        Room room = roomRepository.findById(roomId).orElse(null);

        if (room != null && "DIRECT".equalsIgnoreCase(room.getType())) {
            roomMemberRepository.findByRoomId(roomId).stream()
                    .map(RoomMember::getUserId)
                    .filter(recipientId -> !recipientId.equals(senderId))
                    .findFirst()
                    .ifPresent(recipientId -> {
                        boolean wasMentioned = mentionedUserIds.contains(recipientId);
                        if (!roomPresenceTracker.isViewing(recipientId, roomId)
                                && notificationSettingsService.shouldNotify(recipientId, roomId, wasMentioned)) {
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
        } else {
            fanOutGroupNotifications(room, roomId, senderId, senderName, message, mentionedUserIds);
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
                            && !roomPresenceTracker.isViewing(requesterId, roomId)
                            && notificationSettingsService.shouldNotify(requesterId, roomId, false)) {
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

    private void fanOutGroupNotifications(Room room, String roomId, String senderId, String senderName,
                                          ChatMessage message, Set<String> mentionedUserIds) {
        String roomName = room != null ? room.getName() : "a room";
        String preview = message.content() != null ? message.content() : "Sent an attachment";

        for (RoomMember member : roomMemberRepository.findByRoomId(roomId)) {
            String recipientId = member.getUserId();
            if (recipientId.equals(senderId)) continue;
            if (roomPresenceTracker.isViewing(recipientId, roomId)) continue;

            boolean wasMentioned = mentionedUserIds.contains(recipientId);

            if (wasMentioned) {
                // Mentions break through MENTIONS_ONLY but still respect MUTED
                if (!notificationSettingsService.shouldNotify(recipientId, roomId, true)) continue;
                notificationService.create(
                        recipientId,
                        "MENTION",
                        senderName + " mentioned you in #" + roomName,
                        preview,
                        senderId,
                        roomId
                );
            } else if (notificationSettingsService.shouldNotify(recipientId, roomId, false)) {
                notificationService.create(
                        recipientId,
                        "GROUP_MESSAGE",
                        senderName + " in #" + roomName,
                        preview,
                        senderId,
                        roomId
                );
            }
        }
    }
}
