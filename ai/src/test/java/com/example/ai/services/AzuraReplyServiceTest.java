package com.example.ai.services;

import com.example.ai.config.RoomPresenceTracker;
import com.example.ai.dto.ChatMessage;
import com.example.ai.dto.UserNotice;
import com.example.ai.entity.RoomMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AzuraReplyServiceTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock AzuraService azuraService;
    @Mock RateLimitService rateLimitService;
    @Mock RoomPresenceTracker roomPresenceTracker;
    @Mock RoomNotificationSettingsService notificationSettingsService;
    @Mock NotificationService notificationService;
    @Mock TaskExecutor rejectingExecutor;

    AzuraReplyService service;

    @BeforeEach
    void setUp() {
        service = newService(new SyncTaskExecutor());
    }

    private AzuraReplyService newService(TaskExecutor executor) {
        var s = new AzuraReplyService(messagingTemplate, azuraService, rateLimitService, executor,
                roomPresenceTracker, notificationSettingsService, notificationService);
        ReflectionTestUtils.setField(s, "aiName", "Azura");
        return s;
    }

    @ParameterizedTest
    @ValueSource(strings = {"@ai hello", "hey @AI?", "what do you think @ai", "(@ai) thoughts", "@ai, help", "ok @ai."})
    void detectsAiMentions(String text) {
        assertThat(AzuraReplyService.mentionsAi(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"@aiden hi", "mail me@ai.com", "@ai-bot", "said", "ai please", ""})
    void ignoresNonMentions(String text) {
        assertThat(AzuraReplyService.mentionsAi(text)).isFalse();
    }

    @Test
    void nullIsNotAMention() {
        assertThat(AzuraReplyService.mentionsAi(null)).isFalse();
    }

    @Test
    void rateLimitedRequestGetsPrivateNoticeAndNoModelCall() {
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(false, 42));

        service.requestReply("room1", "u1", "General");

        var notice = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/errors"), notice.capture());
        assertThat(notice.getValue()).isInstanceOfSatisfying(UserNotice.class, n -> {
            assertThat(n.type()).isEqualTo(UserNotice.RATE_LIMITED);
            assertThat(n.retryAfterSeconds()).isEqualTo(42L);
            assertThat(n.roomId()).isEqualTo("room1");
        });
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/room1"), any(Object.class));
        verifyNoInteractions(azuraService);
    }

    @Test
    void fullExecutorGetsBusyNotice() {
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        doThrow(new TaskRejectedException("full")).when(rejectingExecutor).execute(any());

        newService(rejectingExecutor).requestReply("room1", "u1", "General");

        var notice = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("u1"), eq("/queue/errors"), notice.capture());
        assertThat(((UserNotice) notice.getValue()).type()).isEqualTo(UserNotice.AI_BUSY);
    }

    @Test
    void successfulReplyIsSavedAndBroadcastBetweenTypingAndIdle() {
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("hi there"))));
        when(azuraService.generateResponse("room1", "u1")).thenReturn(response);
        when(azuraService.processActionToken("hi there", "u1"))
                .thenReturn(new AzuraService.ProcessedAiResponse("hi there", null));
        RoomMessage saved = RoomMessage.builder().id(99L).roomId("room1").senderId("ai-bot").content("hi there").build();
        when(azuraService.saveAzuraMessage(eq("room1"), eq("hi there"), ArgumentMatchers.<Usage>any(), any())).thenReturn(saved);
        when(roomPresenceTracker.isViewing("u1", "room1")).thenReturn(true);

        service.requestReply("room1", "u1", "General");

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/room/room1"), sent.capture());
        List<String> types = sent.getAllValues().stream().map(m -> ((ChatMessage) m).type()).toList();
        assertThat(types).containsExactly("TYPING", "CHAT", "IDLE");
        ChatMessage chat = (ChatMessage) sent.getAllValues().get(1);
        assertThat(chat.messageId()).isEqualTo(99L);
        assertThat(chat.content()).isEqualTo("hi there");
        verifyNoInteractions(notificationService);
    }

    @Test
    void requesterWhoLeftTheRoomIsNotified() {
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        var response = new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
        when(azuraService.generateResponse("room1", "u1")).thenReturn(response);
        when(azuraService.processActionToken("done", "u1")).thenReturn(new AzuraService.ProcessedAiResponse("done", null));
        when(azuraService.saveAzuraMessage(anyString(), anyString(), ArgumentMatchers.<Usage>any(), any()))
                .thenReturn(RoomMessage.builder().id(1L).build());
        when(roomPresenceTracker.isViewing("u1", "room1")).thenReturn(false);
        when(notificationSettingsService.shouldNotify("u1", "room1", false)).thenReturn(true);

        service.requestReply("room1", "u1", "General");

        verify(notificationService).create("u1", "AI_REPLY", "Azura replied in General", "done", "ai-bot", "room1");
    }

    @Test
    void modelFailureStillClearsTypingIndicator() {
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        when(azuraService.generateResponse("room1", "u1")).thenThrow(new RuntimeException("DeepSeek down"));

        service.requestReply("room1", "u1", "General");

        var sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/room/room1"), sent.capture());
        assertThat(sent.getAllValues()).last()
                .isInstanceOfSatisfying(ChatMessage.class, m -> assertThat(m.type()).isEqualTo("IDLE"));
    }

    @Test
    void emptyContentFallsBackToFriendlyText() {
        assertThat(AzuraReplyService.finalContent(new AzuraService.ProcessedAiResponse(" ", null)))
                .startsWith("Sorry");
        assertThat(AzuraReplyService.finalContent(new AzuraService.ProcessedAiResponse(null,
                new com.example.ai.dto.CalendarActionCard(null, null, null, null, null, null, null, null, null))))
                .contains("calendar action");
    }

    // ---- streaming ----

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private void enableStreaming(AzuraReplyService s) {
        ReflectionTestUtils.setField(s, "streamingEnabled", true);
    }

    private List<ChatMessage> roomEvents() {
        var sent = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/room/room1"), sent.capture());
        return sent.getAllValues().stream().map(ChatMessage.class::cast).toList();
    }

    @Test
    void streamingSendsUpdatesThenFinalMessageWithSameStreamId() {
        enableStreaming(service);
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        var usage = new DefaultUsage(10, 5, 15);
        var last = new ChatResponse(List.of(new Generation(new AssistantMessage(" world"))),
                ChatResponseMetadata.builder().usage(usage).build());
        when(azuraService.streamResponse("room1", "u1"))
                .thenReturn(Flux.just(chunk("Hello there, this is a long enough first chunk"), last));
        when(azuraService.processActionToken("Hello there, this is a long enough first chunk world", "u1"))
                .thenReturn(new AzuraService.ProcessedAiResponse("Hello there, this is a long enough first chunk world", null));
        when(azuraService.saveAzuraMessage(eq("room1"), anyString(), eq((Usage) usage), any()))
                .thenReturn(RoomMessage.builder().id(7L).build());
        when(roomPresenceTracker.isViewing("u1", "room1")).thenReturn(true);

        service.requestReply("room1", "u1", "General");

        List<ChatMessage> events = roomEvents();
        assertThat(events.getFirst().type()).isEqualTo("TYPING");
        assertThat(events.getLast().type()).isEqualTo("IDLE");
        assertThat(events.get(1).type()).as("START comes before any delta").isEqualTo("STREAM_START");
        String streamId = events.get(1).streamId();
        assertThat(streamId).isNotNull();
        assertThat(events).filteredOn(e -> "STREAM_START".equals(e.type())).hasSize(1);

        List<ChatMessage> deltas = events.stream().filter(e -> "STREAM_DELTA".equals(e.type())).toList();
        assertThat(deltas).hasSize(2).allSatisfy(d -> assertThat(d.streamId()).isEqualTo(streamId));
        assertThat(deltas).extracting(ChatMessage::content)
                .as("deltas carry only new text, and concatenate to the reply")
                .containsExactly("Hello there, this is a long enough first chunk", " world");

        ChatMessage end = events.get(events.size() - 2);
        assertThat(end.type()).isEqualTo("CHAT");
        assertThat(end.messageId()).isEqualTo(7L);
        assertThat(end.streamId()).isEqualTo(streamId);
        verify(azuraService, never()).generateResponse(anyString(), anyString());
    }

    @Test
    void streamingNeverShowsTheActionMarker() {
        enableStreaming(service);
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        String id = "123e4567-e89b-12d3-a456-426614174000";
        // Marker split across chunks, each chunk big enough to trigger a flush.
        when(azuraService.streamResponse("room1", "u1")).thenReturn(Flux.fromStream(Stream.of(
                "I've staged that calendar event for you, please approve it. AZU",
                "RA_ACTION_" + id.substring(0, 10),
                id.substring(10) + " Anything else I can help with today, friend?")
                .map(AzuraReplyServiceTest::chunk)));
        when(azuraService.processActionToken(anyString(), eq("u1")))
                .thenReturn(new AzuraService.ProcessedAiResponse("cleaned", null));
        when(azuraService.saveAzuraMessage(anyString(), anyString(), ArgumentMatchers.<Usage>any(), any()))
                .thenReturn(RoomMessage.builder().id(8L).build());
        when(roomPresenceTracker.isViewing("u1", "room1")).thenReturn(true);

        service.requestReply("room1", "u1", "General");

        String shown = roomEvents().stream().filter(e -> "STREAM_DELTA".equals(e.type()))
                .map(ChatMessage::content).reduce("", String::concat);
        assertThat(shown).isNotEmpty()
                .startsWith("I've staged that calendar event")
                .contains("Anything else")
                .doesNotContain("AZU").doesNotContain("ACTION").doesNotContain("123e4567");
    }

    @Test
    void streamFailureSendsStreamErrorToTheRoom() {
        enableStreaming(service);
        when(rateLimitService.tryConsumeAi("u1")).thenReturn(new RateLimitService.Decision(true, 0));
        when(azuraService.streamResponse("room1", "u1")).thenReturn(Flux.concat(
                Flux.just(chunk("Partial answer that is definitely long enough to flush")),
                Flux.error(new RuntimeException("connection reset"))));

        service.requestReply("room1", "u1", "General");

        List<ChatMessage> events = roomEvents();
        assertThat(events).extracting(ChatMessage::type)
                .containsSubsequence("TYPING", "STREAM_START", "STREAM_DELTA", "STREAM_ERROR", "IDLE");
        ChatMessage error = events.stream().filter(e -> "STREAM_ERROR".equals(e.type())).findFirst().orElseThrow();
        assertThat(error.streamId()).isEqualTo(events.get(1).streamId());
        verify(azuraService, never()).saveAzuraMessage(anyString(), anyString(), ArgumentMatchers.<Usage>any(), any());
    }
}
