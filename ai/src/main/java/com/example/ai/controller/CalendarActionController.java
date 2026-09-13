package com.example.ai.controller;

import com.example.ai.dto.CalendarActionCard;
import com.example.ai.entity.User;
import com.example.ai.services.CalendarApiClient;
import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarAction;
import com.example.ai.services.PendingCalendarActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendar/actions")
public class CalendarActionController {

    private final PendingCalendarActionService pendingCalendarActionService;
    private final GoogleOAuthService googleOAuthService;
    private final CalendarApiClient calendarApiClient;

    @GetMapping("/{actionId}")
    public ResponseEntity<CalendarActionCard> getAction(@AuthenticationPrincipal User user,
                                                        @PathVariable String actionId) {
        return pendingCalendarActionService.getOwnedPending(actionId, user.getId())
                .flatMap(a -> pendingCalendarActionService.cardFor(actionId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{actionId}/confirm")
    public ResponseEntity<Map<String, String>> confirm(@AuthenticationPrincipal User user,
                                                       @PathVariable String actionId) {
        PendingCalendarAction action;
        try {
            action = pendingCalendarActionService.complete(actionId, user.getId(),
                    PendingCalendarAction.STATUS_APPROVED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }

        var token = googleOAuthService.validAccessToken(action.getUserId());
        if (token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Google Calendar is no longer connected. Reconnect it from your profile first."));
        }

        try {
            String result = calendarApiClient.createEvent(
                    token.get(),
                    action.getCalendarId(),
                    action.getSummary(),
                    action.getStartTime(),
                    action.getEndTime(),
                    action.getDescription(),
                    action.getLocation());
            log.info("Calendar event created via action {} for user {}", actionId, user.getId());
            return ResponseEntity.ok(Map.of("status", "APPROVED", "message", result));
        } catch (Exception e) {
            log.error("Failed to create calendar event for action {}", actionId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Google could not create the event: " + e.getMessage()));
        }
    }

    @PostMapping("/{actionId}/decline")
    public ResponseEntity<Map<String, String>> decline(@AuthenticationPrincipal User user,
                                                       @PathVariable String actionId) {
        try {
            pendingCalendarActionService.complete(actionId, user.getId(),
                    PendingCalendarAction.STATUS_DECLINED);
            return ResponseEntity.ok(Map.of("status", "DECLINED", "message", "Calendar event cancelled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }
}