package com.example.ai.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Thin wrapper around the Google Calendar v3 events endpoint. Shared by the calendar
 * tools (read-only listing) and the action-confirmation flow (event creation).
 */
@Service
public class CalendarApiClient {

    static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3";
    private static final Pattern ALL_DAY_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String createEvent(String accessToken, String calendarId, String summary,
                              String startTime, String endTime, String description, String location) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        if (description != null && !description.isBlank()) {
            payload.put("description", description);
        }
        if (location != null && !location.isBlank()) {
            payload.put("location", location);
        }
        payload.put("start", timeObject(startTime));
        payload.put("end", timeObject(endTime));

        String url = CALENDAR_API + "/calendars/" + urlEncode(calendarId != null && !calendarId.isBlank() ? calendarId : "primary")
                + "/events";
        String body = objectMapper.writeValueAsString(payload);
        HttpResponse<String> response = post(url, accessToken, body);
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode error = root.path("error");
        if (!error.isMissingNode()) {
            throw new CalendarApiException(error.path("message").asText("Google API error"));
        }

        String id = root.path("id").asText("");
        String htmlLink = root.path("htmlLink").asText("");
        String createdEnd = root.path("end").path("dateTime").asText(
                root.path("end").path("date").asText(endTime));
        StringBuilder out = new StringBuilder("Calendar event created: \"").append(summary)
                .append("\" (ends ").append(createdEnd).append(")");
        if (!id.isBlank()) {
            out.append(" id=").append(id);
        }
        if (!htmlLink.isBlank()) {
            out.append(" ").append(htmlLink);
        }
        return out.toString();
    }

    private Map<String, Object> timeObject(String value) {
        Map<String, Object> obj = new LinkedHashMap<>();
        if (ALL_DAY_DATE.matcher(value.trim()).matches()) {
            obj.put("date", value.trim());
        } else {
            obj.put("dateTime", value.trim());
        }
        return obj;
    }

    private HttpResponse<String> post(String url, String accessToken, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class CalendarApiException extends RuntimeException {
        public CalendarApiException(String message) {
            super(message);
        }
    }
}