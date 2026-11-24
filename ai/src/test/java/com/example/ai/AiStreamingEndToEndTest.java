package com.example.ai;

import com.example.ai.support.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The whole "@ai" path over a real server and a real STOMP/SockJS client:
 * JWT handshake, room authorization, rate limiting, the AI executor, streaming events, persistence,
 * and private notices. Only the DeepSeek model is stubbed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AiStreamingEndToEndTest {

    @LocalServerPort
    int port;

    // Not reset between tests: AI tasks queued by one test may still run on the AI pool after it finishes.
    @MockitoBean(reset = MockReset.NONE)
    DeepSeekChatModel chatModel;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void stubModel() {
        when(chatModel.getDefaultOptions()).thenReturn(DeepSeekChatOptions.builder().build());
        when(chatModel.getOptions()).thenReturn(DeepSeekChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenAnswer(inv -> Flux.just(
                        chunk("Sure! Friday at 5pm works ", null),
                        chunk("for everyone in this room, ", null),
                        chunk("see you all there.", new DefaultUsage(120, 12, 132)))
                .delayElements(Duration.ofMillis(100)));
    }

    private static ChatResponse chunk(String text, DefaultUsage usage) {
        var generations = List.of(new Generation(new AssistantMessage(text)));
        return usage == null
                ? new ChatResponse(generations)
                : new ChatResponse(generations, ChatResponseMetadata.builder().usage(usage).build());
    }

    @Test
    void aiReplyStreamsToTheRoomAndIsSaved() throws Exception {
        var user = register();
        String roomId = createRoom(user.token());
        StompSession session = connect(user.token());
        BlockingQueue<Map<String, Object>> room = subscribe(session, "/topic/room/" + roomId);

        session.send("/app/chat/" + roomId, Map.of("content", "@ai when should we meet?", "type", "CHAT"));

        Map<String, Object> userMsg = next(room);
        assertThat(userMsg).containsEntry("type", "CHAT").containsEntry("senderId", user.userId());
        assertThat(next(room)).containsEntry("type", "TYPING");

        Map<String, Object> start = next(room);
        assertThat(start).containsEntry("type", "STREAM_START").containsEntry("senderId", "ai-bot");
        String streamId = (String) start.get("streamId");
        assertThat(streamId).isNotBlank();

        Map<String, Object> event;
        StringBuilder streamed = new StringBuilder();
        int deltas = 0;
        while ("STREAM_DELTA".equals((event = next(room)).get("type"))) {
            deltas++;
            assertThat(event).containsEntry("streamId", streamId);
            streamed.append(event.get("content"));
        }
        assertThat(deltas).isGreaterThanOrEqualTo(2);
        assertThat(streamed.toString()).as("deltas concatenate to the reply")
                .isEqualTo("Sure! Friday at 5pm works for everyone in this room, see you all there.");

        assertThat(event).containsEntry("type", "CHAT")
                .containsEntry("senderId", "ai-bot")
                .containsEntry("streamId", streamId)
                .containsEntry("content", "Sure! Friday at 5pm works for everyone in this room, see you all there.");
        assertThat(event.get("messageId")).isNotNull();
        assertThat(next(room)).containsEntry("type", "IDLE");

        String history = get("/api/v1/rooms/" + roomId + "/messages", user.token());
        List<String> contents = JsonPath.read(history, "$[*].content");
        assertThat(contents).contains("Sure! Friday at 5pm works for everyone in this room, see you all there.");

        session.disconnect();
    }

    @Test
    void overLimitRequesterGetsPrivateNoticeOnly() throws Exception {
        var user = register();
        String roomId = createRoom(user.token());
        StompSession session = connect(user.token());
        BlockingQueue<Map<String, Object>> notices = subscribe(session, "/user/queue/errors");

        // The test profile allows 3 AI requests per minute.
        for (int i = 0; i < 4; i++) {
            session.send("/app/chat/" + roomId, Map.of("content", "@ai ping " + i, "type", "CHAT"));
        }

        Map<String, Object> notice = next(notices);
        assertThat(notice).containsEntry("type", "RATE_LIMITED").containsEntry("roomId", roomId);
        assertThat(((Number) notice.get("retryAfterSeconds")).longValue()).isPositive();

        session.disconnect();
    }

    @Test
    void noticesReachOnlyTheUserTheyAreFor() throws Exception {
        var me = register();
        var other = register();
        BlockingQueue<Map<String, Object>> mine = subscribe(connect(me.token()), "/user/queue/errors");

        String roomId = createRoom(other.token());
        StompSession otherSession = connect(other.token());
        BlockingQueue<Map<String, Object>> theirs = subscribe(otherSession, "/user/queue/errors");
        for (int i = 0; i < 4; i++) {
            otherSession.send("/app/chat/" + roomId, Map.of("content", "@ai hi " + i, "type", "CHAT"));
        }

        assertThat(next(theirs)).containsEntry("type", "RATE_LIMITED");
        assertThat(mine.poll(2, TimeUnit.SECONDS)).as("another user's notices must not leak").isNull();
    }

    @Test
    void wordsThatMerelyContainAtAiDoNotWakeTheAi() throws Exception {
        var user = register();
        String roomId = createRoom(user.token());
        StompSession session = connect(user.token());
        BlockingQueue<Map<String, Object>> room = subscribe(session, "/topic/room/" + roomId);
        BlockingQueue<Map<String, Object>> notices = subscribe(session, "/user/queue/errors");

        for (String text : List.of("mail me@ai.com please", "hey @aiden", "ask @ai-bot", "said hi")) {
            session.send("/app/chat/" + roomId, Map.of("content", text, "type", "CHAT"));
            assertThat(next(room)).containsEntry("type", "CHAT").containsEntry("content", text);
        }

        assertThat(room.poll(2, TimeUnit.SECONDS)).as("no TYPING/STREAM events: the AI was not triggered").isNull();
        assertThat(notices.poll(0, TimeUnit.SECONDS)).as("and no rate limit was consumed into a notice").isNull();
    }

    // ---- helpers ----

    private record User(String userId, String token) {
    }

    private User register() throws Exception {
        String name = "e2e_" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"username":"%s","password":"password123","displayName":"%s","email":"%s@example.com"}
                """.formatted(name, name, name);
        String json = send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "192.0.2." + (int) (Math.random() * 250 + 1))
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        return new User(JsonPath.read(json, "$.userId"), JsonPath.read(json, "$.token"));
    }

    private String createRoom(String token) throws Exception {
        String json = send(HttpRequest.newBuilder(uri("/api/v1/rooms"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"e2e room\"}")));
        return JsonPath.read(json, "$.id");
    }

    private String get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).GET());
    }

    private String send(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private StompSession connect(String token) throws Exception {
        var client = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new JacksonJsonMessageConverter());
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        var handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:5173");
        return client.connectAsync("http://localhost:" + port + "/ws-chat", handshake, connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) throws Exception {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // let the SUBSCRIBE frame reach the broker before we publish
        return queue;
    }

    private static Map<String, Object> next(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> event = queue.poll(10, TimeUnit.SECONDS);
        assertThat(event).as("expected another event").isNotNull();
        return event;
    }
}
