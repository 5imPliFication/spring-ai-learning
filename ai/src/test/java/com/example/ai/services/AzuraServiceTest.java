package com.example.ai.services;

import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.repository.RoomMessageRepository;
import com.example.ai.repository.UserRepository;
import com.example.ai.tools.CalendarTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AzuraService with a real ChatClient over a mocked model, so we can inspect the exact prompt sent. */
@ExtendWith(MockitoExtension.class)
class AzuraServiceTest {

    @Mock RoomMessageRepository roomMessageRepository;
    @Mock UserRepository userRepository;
    @Mock ChatModel chatModel;

    AzuraService service;

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getOptions()).thenReturn(DeepSeekChatOptions.builder().build());
        lenient().when(chatModel.getDefaultOptions()).thenReturn(DeepSeekChatOptions.builder().build());
        var calendarTools = new CalendarTools(mock(GoogleOAuthService.class), new PendingCalendarActionService());
        service = new AzuraService(roomMessageRepository, userRepository, ChatClient.builder(chatModel).build(),
                calendarTools, new PendingCalendarActionService());
        ReflectionTestUtils.setField(service, "aiName", "Azura");
        ReflectionTestUtils.setField(service, "systemPromptConfig", "Be nice.");
        ReflectionTestUtils.setField(service, "maxTokens", 300);
        ReflectionTestUtils.setField(service, "contextMessagesCount", 30);
    }

    private static RoomMessage msg(String sender, String content) {
        return RoomMessage.builder().senderId(sender).messageType("TEXT").content(content).build();
    }

    private void givenRecentMessagesNewestFirst(RoomMessage... newestFirst) {
        when(roomMessageRepository.findByRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq("room1"), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(newestFirst)));
        when(userRepository.findAllById(anyIterable())).thenReturn(List.of(
                User.builder().id("u1").displayName("Alice").build(),
                User.builder().id("u2").displayName("Bob").build()));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void promptContainsChronologicalTranscriptWithNamesAndSystemInstructions() {
        givenRecentMessagesNewestFirst(msg("u2", "@ai what do you think?"), msg("ai-bot", "hi!"), msg("u1", "pizza tonight?"));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("Pizza sounds great"));

        ChatResponse response = service.generateResponse("room1", "u1");

        assertThat(response.getResult().getOutput().getText()).isEqualTo("Pizza sounds great");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String system = prompt.getValue().getInstructions().stream()
                .filter(m -> m.getMessageType() == MessageType.SYSTEM).findFirst().orElseThrow().getText();
        String user = prompt.getValue().getUserMessage().getText();

        assertThat(system).contains("Your name is Azura").contains("Be nice.").contains("Today's date is");
        assertThat(user).contains("""
                Alice: pizza tonight?
                Azura: hi!
                Bob: @ai what do you think?
                """);
        assertThat(user.indexOf("pizza tonight")).isLessThan(user.indexOf("what do you think"));
    }

    @Test
    void contextSizeFollowsConfigurationInsteadOfAFixedTwenty() {
        givenRecentMessagesNewestFirst(msg("u1", "hi"));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("hey"));

        service.generateResponse("room1", "u1");

        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(roomMessageRepository).findByRoomIdAndDeletedAtIsNullOrderByCreatedAtDesc(eq("room1"), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(30);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    void calendarToolsAndMaxTokensAreSentForARequester() {
        givenRecentMessagesNewestFirst(msg("u1", "@ai book friday"));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply("ok"));

        service.generateResponse("room1", "u1");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        var options = (ToolCallingChatOptions) prompt.getValue().getOptions();
        assertThat(options.getMaxTokens()).isEqualTo(300);
        assertThat(options.getToolCallbacks()).extracting(cb -> cb.getToolDefinition().name())
                .contains("list_my_calendars", "create_my_calendar_event");
        assertThat(options.getToolContext()).containsEntry("userId", "u1").containsEntry("roomId", "room1");
    }

    @Test
    void streamResponseSendsTheSamePromptAndReturnsChunks() {
        givenRecentMessagesNewestFirst(msg("u1", "@ai hi"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(reply("Hel"), reply("lo")));

        List<ChatResponse> chunks = service.streamResponse("room1", "u1").collectList().block();

        assertThat(chunks).hasSize(2);
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).contains("Alice: @ai hi");
    }

    @Test
    void emptyModelReplyFallsBackToApologyAfterProcessing() {
        givenRecentMessagesNewestFirst(msg("u1", "@ai ?"));
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(""));

        String raw = service.generateResponse("room1", "u1").getResult().getOutput().getText();
        var processed = service.processActionToken(raw, "u1");

        assertThat(AzuraReplyService.finalContent(processed))
                .isEqualTo("Sorry, I couldn't generate a response. Please try again.");
    }
}
