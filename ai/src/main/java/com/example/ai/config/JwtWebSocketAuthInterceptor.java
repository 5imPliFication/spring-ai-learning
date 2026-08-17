package com.example.ai.config;

import com.example.ai.entity.User;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

@RequiredArgsConstructor
public class JwtWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String NOTIFICATION_TOPIC_PREFIX = "/topic/user/";
    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";
    private static final String CHAT_DESTINATION_PREFIX = "/app/chat/";

    private final JwtUtil jwtUtil;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> authorizeSend(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null || !jwtUtil.isValid(token)) {
            throw new MessageDeliveryException("Unauthorized WebSocket connection: missing or invalid token");
        }

        User user = User.builder()
                .id(jwtUtil.extractUserId(token))
                .username(jwtUtil.extractUsername(token))
                .displayName(jwtUtil.extractDisplayName(token))
                .role(jwtUtil.extractRole(token))
                .build();

        var authentication = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        accessor.setUser(authentication);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        User user = authenticatedUser(accessor);
        if (user == null) {
            throw new MessageDeliveryException("Authentication required to subscribe");
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        if (destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            String targetUserId = destination.substring(NOTIFICATION_TOPIC_PREFIX.length()).split("/")[0];
            if (!user.getId().equals(targetUserId)) {
                throw new MessageDeliveryException("Cannot subscribe to another user's notifications");
            }
        } else if (destination.startsWith(ROOM_TOPIC_PREFIX)) {
            String roomId = destination.substring(ROOM_TOPIC_PREFIX.length()).split("/")[0];
            if (!canAccessRoom(user, roomId)) {
                throw new MessageDeliveryException("Not a member of this room");
            }
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        User user = authenticatedUser(accessor);
        if (user == null) {
            throw new MessageDeliveryException("Authentication required to send messages");
        }

        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(CHAT_DESTINATION_PREFIX)) {
            String roomId = destination.substring(CHAT_DESTINATION_PREFIX.length()).split("/")[0];
            if (!canAccessRoom(user, roomId)) {
                throw new MessageDeliveryException("Not a member of this room");
            }
        }
    }

    private boolean canAccessRoom(User user, String roomId) {
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        return roomRepository.findByIdAndDeletedAtIsNull(roomId)
                .map(room -> user.getId().equals(room.getCreatedBy())
                        || roomMemberRepository.existsByRoomIdAndUserId(roomId, user.getId()))
                .orElse(false);
    }

    private User authenticatedUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return accessor.getFirstNativeHeader("token");
    }
}