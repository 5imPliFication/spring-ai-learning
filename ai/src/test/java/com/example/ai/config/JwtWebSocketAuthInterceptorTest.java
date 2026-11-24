package com.example.ai.config;

import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtWebSocketAuthInterceptorTest {

    @Mock JwtUtil jwtUtil;
    @Mock RoomRepository roomRepository;
    @Mock RoomMemberRepository roomMemberRepository;
    @Mock RoomPresenceTracker roomPresenceTracker;

    JwtWebSocketAuthInterceptor interceptor;
    final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        interceptor = new JwtWebSocketAuthInterceptor(jwtUtil, roomRepository, roomMemberRepository, roomPresenceTracker);
    }

    private Principal connect() {
        when(jwtUtil.isValid("tok")).thenReturn(true);
        when(jwtUtil.extractUserId("tok")).thenReturn("user-123");
        when(jwtUtil.extractUsername("tok")).thenReturn("alice");
        when(jwtUtil.extractDisplayName("tok")).thenReturn("Alice");
        when(jwtUtil.extractRole("tok")).thenReturn("USER");
        when(jwtUtil.extractEmail("tok")).thenReturn("a@example.com");
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer tok");
        accessor.setLeaveMutable(true);
        Message<?> result = interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
        return StompHeaderAccessor.wrap(result).getUser();
    }

    /** convertAndSendToUser(userId, ...) routes by Principal#getName(), so it must be the user id. */
    @Test
    void principalNameIsTheUserId() {
        assertThat(connect().getName()).isEqualTo("user-123");
    }

    @Test
    void principalStillExposesTheUserForControllers() {
        Principal principal = connect();
        assertThat(principal).isInstanceOfSatisfying(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class,
                auth -> assertThat(auth.getPrincipal()).isInstanceOfSatisfying(
                        com.example.ai.entity.User.class, u -> assertThat(u.getId()).isEqualTo("user-123")));
    }

    private Message<?> subscribe(Principal user, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("s1");
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel);
    }

    @Test
    void userQueueSubscriptionIsAllowed() {
        assertThat(subscribe(connect(), "/user/queue/errors")).isNotNull();
    }

    @Test
    void directSubscriptionToResolvedQueueIsRejected() {
        Principal me = connect();
        assertThatThrownBy(() -> subscribe(me, "/queue/errors-usersomeothersession"))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
