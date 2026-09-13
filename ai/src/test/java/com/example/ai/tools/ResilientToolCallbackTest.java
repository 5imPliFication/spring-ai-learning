package com.example.ai.tools;

import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarActionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientToolCallbackTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private PendingCalendarActionService pendingCalendarActionService;

    private ToolCallback calendarCallback() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(new CalendarTools(googleOAuthService, pendingCalendarActionService))
                .build()
                .getToolCallbacks();
        return List.of(callbacks).stream()
                .filter(cb -> "list_my_calendar_events".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void repairsTrailingCommaAndStillInvokesTool() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.empty());

        String result = new ResilientToolCallback(calendarCallback()).call(
                "{\"calendarId\":\"primary\",\"timeMin\":\"2026-09-13T00:00:00Z\",\"timeMax\":\"2026-09-20T00:00:00Z\",}",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("hasn't connected their Google Calendar");
    }

    @Test
    void repairsDoubleEncodedStringArguments() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.empty());

        String result = new ResilientToolCallback(calendarCallback()).call(
                "\"{\\\"calendarId\\\":\\\"primary\\\"}\"",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("hasn't connected their Google Calendar");
    }

    @Test
    void returnsInstructionInsteadOfThrowingForUnparseableInput() {
        String result = new ResilientToolCallback(calendarCallback()).call(
                "not json at all",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("could not be parsed as JSON")
                .contains("list_my_calendar_events");
    }

    @Test
    void returnsRecognizableEmptyInputError() {
        String result = new ResilientToolCallback(calendarCallback()).call("", new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("could not be parsed as JSON");
    }

    @Test
    void passesThroughWithValidJsonUnchanged() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.empty());

        String result = new ResilientToolCallback(calendarCallback()).call(
                "{\"calendarId\":\"primary\",\"timeMin\":\"2026-09-13T00:00:00Z\",\"timeMax\":\"2026-09-20T00:00:00Z\"}",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("hasn't connected their Google Calendar");
    }

    @Test
    void exposesDelegateToolDefinition() {
        ToolCallback delegate = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("fake_tool")
                        .description("fake")
                        .inputSchema("{\"type\":\"object\"}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                return "ok";
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return "ok";
            }
        };

        assertThat(new ResilientToolCallback(delegate).getToolDefinition().name()).isEqualTo("fake_tool");
    }
}