package com.example.ai.config;

import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomPresenceTracker roomPresenceTracker;
    private final AllowedOrigins allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /queue backs per-user destinations: convertAndSendToUser(userId, "/queue/errors", ...) reaches
        // the sessions of that user, who subscribe to /user/queue/errors.
        config.enableSimpleBroker("/topic", "/queue");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins.patternsArray())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtWebSocketAuthInterceptor(
                jwtUtil, roomRepository, roomMemberRepository, roomPresenceTracker));
    }
}