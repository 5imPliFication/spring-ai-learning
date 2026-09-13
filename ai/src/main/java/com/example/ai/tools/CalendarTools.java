package com.example.ai.tools;

import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarAction;
import com.example.ai.services.PendingCalendarActionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class CalendarTools {

    private static final String USER_CTX_KEY = "userId";
    private static final String ROOM_CTX_KEY = "roomId";
    public static final String ACTION_TOKEN_PREFIX = "AZURA_ACTION_";
    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";

    private final GoogleOAuthService googleOAuthService;
    private final PendingCalendarActionService pendingCalendarActionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CalendarTools(GoogleOAuthService googleOAuthService,
                         PendingCalendarActionService pendingCalendarActionService) {
        this.googleOAuthService = googleOAuthService;
        this.pendingCalendarActionService = pendingCalendarActionService;
    }

    @Tool(name = "list_my_calendars",
            description = "List the Google calendars available in the requesting user's connected Google account. "
                    + "Use before creating an event in a non-primary calendar or when the user asks about their calendars.")
    public String listMyCalendars(ToolContext ctx) {
        Optional<String> token = googleOAuthService.validAccessToken(userId(ctx));
        if (token.isEmpty()) {
            return notConnected(ctx);
        }

        String url = CALENDAR_API + "/users/me/calendarList?maxResults=25";
        try {
            HttpResponse<String> response = get(url, token.get());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                return "Google API error: " + error.path("message").asText("unknown error");
            }

            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "No calendars found in the connected Google account.";
            }

            StringBuilder sb = new StringBuilder("Calendars in the connected account:\n");
            for (JsonNode item : items) {
                String summary = item.path("summary").asText("(untitled)");
                String id = item.path("id").asText();
                boolean primary = item.path("primary").asBoolean();
                String accessRole = item.path("accessRole").asText("reader");
                sb.append("- ").append(summary)
                        .append(" [").append(id).append("]")
                        .append(primary ? " (primary)" : "")
                        .append(" (").append(accessRole).append(")\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to list calendars", e);
            return "I had trouble reading the calendar list: " + e.getMessage();
        }
    }

    @Tool(name = "list_my_calendar_events",
            description = "List events from a Google Calendar between two times. Use 'primary' as calendarId for the user's "
                    + "main calendar. timeMin and timeMax are RFC3339 timestamps such as 2026-09-14T00:00:00Z. "
                    + "Only call after the user asks about their schedule.")
    public String listMyCalendarEvents(
            @ToolParam(description = "Calendar id; use 'primary' for the main calendar") String calendarId,
            @ToolParam(description = "Start of the window as RFC3339, e.g. 2026-09-14T00:00:00Z") String timeMin,
            @ToolParam(description = "End of the window as RFC3339, e.g. 2026-09-20T00:00:00Z") String timeMax,
            @ToolParam(description = "Maximum number of events to return; default 10") Integer maxResults,
            ToolContext ctx) {
        Optional<String> token = googleOAuthService.validAccessToken(userId(ctx));
        if (token.isEmpty()) {
            return notConnected(ctx);
        }

        StringBuilder url = new StringBuilder(CALENDAR_API)
                .append("/calendars/").append(urlEncode(calendarId != null ? calendarId : "primary"))
                .append("/events?singleEvents=true&orderBy=startTime")
                .append("&maxResults=").append(maxResults != null ? maxResults : 10);
        if (timeMin != null && !timeMin.isBlank()) {
            url.append("&timeMin=").append(urlEncode(timeMin));
        }
        if (timeMax != null && !timeMax.isBlank()) {
            url.append("&timeMax=").append(urlEncode(timeMax));
        }

        try {
            HttpResponse<String> response = get(url.toString(), token.get());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                return "Google API error: " + error.path("message").asText("unknown error");
            }

            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "No events found in that time window.";
            }

            StringBuilder sb = new StringBuilder("Events in the requested window:\n");
            for (JsonNode item : items) {
                String summary = item.path("summary").asText("(untitled)");
                String status = item.path("status").asText("confirmed");
                String start = item.path("start").path("dateTime").asText(
                        item.path("start").path("date").asText("?"));
                sb.append("- ").append(summary)
                        .append(" | ").append(start)
                        .append(" | ").append(status)
                        .append(" | id=").append(item.path("id").asText("")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to list calendar events", e);
            return "I had trouble listing the events: " + e.getMessage();
        }
    }

    @Tool(name = "create_my_calendar_event",
            description = "Stage a new event on the user's Google Calendar so they can approve it before it is created. "
                    + "The event is NEVER created directly; it is only created after the user approves the confirmation card. "
                    + "startTime and endTime are RFC3339 timestamps such as 2026-09-14T15:00:00+02:00, or a plain date such as "
                    + "2026-09-14 for an all-day event. IMPORTANT: calendarId, summary, startTime and endTime are REQUIRED. "
                    + "Do NOT call this tool unless the user has already provided the title and the start/end time. "
                    + "If any of them are missing, ask the user a short follow-up question to collect them instead of calling this tool.")
    public String createMyCalendarEvent(
            @ToolParam(description = "Calendar id; use 'primary' for the main calendar") String calendarId,
            @ToolParam(description = "Event title") String summary,
            @ToolParam(description = "Start time as RFC3339 or an all-day date") String startTime,
            @ToolParam(description = "End time as RFC3339 or an all-day date (inclusive for no-time dates)") String endTime,
            @ToolParam(description = "Optional event description", required = false) String description,
            @ToolParam(description = "Optional event location", required = false) String location,
            ToolContext ctx) {
        Optional<String> token = googleOAuthService.validAccessToken(userId(ctx));
        if (token.isEmpty()) {
            return notConnected(ctx);
        }

        if (summary == null || summary.isBlank() || startTime == null || startTime.isBlank()
                || endTime == null || endTime.isBlank()) {
            return "Missing required details before creating the event. Ask the user for the event title and the "
                    + "start/end time (RFC3339 like 2026-09-14T15:00:00+02:00 or just a date like 2026-09-14). "
                    + "Do not guess the times and do not call this tool again until the user has provided them.";
        }

        PendingCalendarAction action = pendingCalendarActionService.stage(
                userId(ctx), roomId(ctx), calendarId, summary, startTime, endTime, description, location);

return "Event staged for approval: \"" + summary + "\". "
                + "Token: " + ACTION_TOKEN_PREFIX + action.getActionId() + ". "
                + "You MUST include this exact token verbatim in your reply to the user so the system can render "
                + "an approval card. Do not create the event yourself and do not reveal that the token was used.";
    }

    private String userId(ToolContext ctx) {
        return stringCtxValue(ctx, USER_CTX_KEY);
    }

    private String roomId(ToolContext ctx) {
        return stringCtxValue(ctx, ROOM_CTX_KEY);
    }

    private String stringCtxValue(ToolContext ctx, String key) {
        if (ctx == null || ctx.getContext() == null) {
            return null;
        }
        Object value = ctx.getContext().get(key);
        return value instanceof String s ? s : null;
    }

    private String notConnected(ToolContext ctx) {
        if (userId(ctx) == null) {
            return "The requesting user is not identified, so I can't access any calendar.";
        }
        return "This user hasn't connected their Google Calendar yet. Ask them to open their profile "
                + "(click their avatar) and connect Google Calendar first.";
    }

    private HttpResponse<String> get(String url, String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}