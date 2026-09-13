package com.example.ai.tools;

import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarAction;
import com.example.ai.services.PendingCalendarActionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarToolsTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private PendingCalendarActionService pendingCalendarActionService;

    private CalendarTools tools() {
        return new CalendarTools(googleOAuthService, pendingCalendarActionService);
    }

    private ToolCallback callback(String name) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tools())
                .build()
                .getToolCallbacks();
        return List.of(callbacks).stream()
                .filter(cb -> name.equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void registersAllCalendarTools() {
        List<String> names = List.of(
                MethodToolCallbackProvider.builder()
                        .toolObjects(tools())
                        .build()
                        .getToolCallbacks()
        ).stream()
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder(
                "list_my_calendars",
                "list_my_calendar_events",
                "create_my_calendar_event");
    }

    @Test
    void createEventSchemaExcludesToolContextParameter() {
        String schema = callback("create_my_calendar_event").getToolDefinition().inputSchema();

        assertThat(schema).contains("summary")
                .contains("startTime")
                .contains("endTime")
                .doesNotContain("ToolContext")
                .doesNotContain("\"ctx\"");
    }

    @Test
    void listEventsWithoutConnectionTellsUserToConnect() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.empty());

        String result = callback("list_my_calendar_events").call(
                "{\"calendarId\":\"primary\",\"timeMin\":\"2026-09-13T00:00:00Z\",\"timeMax\":\"2026-09-20T00:00:00Z\"}",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("hasn't connected their Google Calendar");
    }

    @Test
    void listEventsWithBlankUserIdPromptsConnect() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.empty());

        String result = callback("list_my_calendar_events").call(
                "{\"calendarId\":\"primary\",\"timeMin\":\"2026-09-13T00:00:00Z\",\"timeMax\":\"2026-09-20T00:00:00Z\"}",
                new ToolContext(Map.of("userId", "")));

        assertThat(result).contains("hasn't connected their Google Calendar");
    }

    @Test
    void createEventRejectsMissingRequiredFieldsBeforeCallingGoogle() {
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.of("token"));

        String result = callback("create_my_calendar_event").call(
                "{\"calendarId\":\"primary\",\"summary\":\"\",\"startTime\":\"\",\"endTime\":\"\"}",
                new ToolContext(Map.of("userId", "user-1")));

        assertThat(result).contains("Missing required details");
    }

    @Test
    void createEventStagesActionAndReturnsToken() {
        String actionId = "550e8400-e29b-41d4-a716-446655440000";
        when(googleOAuthService.validAccessToken(anyString())).thenReturn(Optional.of("token"));
        when(pendingCalendarActionService.stage(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PendingCalendarAction(
                        actionId, "user-1", "room-1", "primary", "Meeting",
                        "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", null, null,
                        java.time.Instant.now().plus(java.time.Duration.ofMinutes(10)),
                        PendingCalendarAction.STATUS_PENDING));

        String result = callback("create_my_calendar_event").call(
                "{\"calendarId\":\"primary\",\"summary\":\"Meeting\",\"startTime\":\"2026-09-14T10:00:00Z\",\"endTime\":\"2026-09-14T11:00:00Z\"}",
                new ToolContext(Map.of("userId", "user-1", "roomId", "room-1")));

        assertThat(result).contains("AZURA_ACTION_" + actionId);
        verify(pendingCalendarActionService).stage(
                "user-1", "room-1", "primary", "Meeting",
                "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", null, null);
    }
}