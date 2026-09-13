package com.example.ai.controller;

import com.example.ai.dto.CalendarActionCard;
import com.example.ai.entity.User;
import com.example.ai.services.CalendarApiClient;
import com.example.ai.services.GoogleOAuthService;
import com.example.ai.services.PendingCalendarActionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarActionControllerTest {

    @Mock
    private PendingCalendarActionService pendingCalendarActionService;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private CalendarApiClient calendarApiClient;

    private final CalendarActionController controller() {
        return new CalendarActionController(pendingCalendarActionService, googleOAuthService, calendarApiClient);
    }

    private User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void getActionReturnsCardForOwner() {
        when(pendingCalendarActionService.getOwnedPending("abc", "user-1"))
                .thenReturn(Optional.of(new com.example.ai.services.PendingCalendarAction(
                        "abc", "user-1", "room-1", "primary", "Meeting",
                        "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", null, null, null,
                        com.example.ai.services.PendingCalendarAction.STATUS_PENDING)));
        when(pendingCalendarActionService.cardFor("abc"))
                .thenReturn(Optional.of(new CalendarActionCard(
                        "abc", "user-1", "primary", "Meeting", "2026-09-14T10:00:00Z",
                        "2026-09-14T11:00:00Z", null, null,
                        com.example.ai.services.PendingCalendarAction.STATUS_PENDING)));

        var response = controller().getAction(user("user-1"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().summary()).isEqualTo("Meeting");
    }

    @Test
    void getActionIsNotFoundForForeignUser() {
        when(pendingCalendarActionService.getOwnedPending("abc", "user-2")).thenReturn(Optional.empty());

        var response = controller().getAction(user("user-2"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void confirmCreatesEventAndMarksApproved() throws Exception {
        String[] captured = new String[1];
        when(pendingCalendarActionService.complete("abc", "user-1",
                com.example.ai.services.PendingCalendarAction.STATUS_APPROVED))
                .thenReturn(new com.example.ai.services.PendingCalendarAction(
                        "abc", "user-1", "room-1", "primary", "Team meeting",
                        "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", "Standup", "Room 1",
                        null, com.example.ai.services.PendingCalendarAction.STATUS_APPROVED));
        when(googleOAuthService.validAccessToken("user-1")).thenReturn(Optional.of("token"));
        when(calendarApiClient.createEvent(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    captured[0] = inv.getArgument(2);
                    return "Calendar event created: \"Team meeting\"";
                });

        var response = controller().confirm(user("user-1"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).containsEntry("status", "APPROVED");
        assertThat(response.getBody()).containsEntry("message", "Calendar event created: \"Team meeting\"");
        assertThat(captured[0]).isEqualTo("Team meeting");
    }

    @Test
    void confirmRejectsAlreadyResolvedAction() {
        when(pendingCalendarActionService.complete("abc", "user-1",
                com.example.ai.services.PendingCalendarAction.STATUS_APPROVED))
                .thenThrow(new IllegalArgumentException("Calendar action not found, expired, or already resolved"));

        var response = controller().confirm(user("user-1"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getBody()).containsEntry("status", "ERROR");
    }

    @Test
    void declineMarksCancelled() {
        when(pendingCalendarActionService.complete("abc", "user-1",
                com.example.ai.services.PendingCalendarAction.STATUS_DECLINED))
                .thenReturn(new com.example.ai.services.PendingCalendarAction(
                        "abc", "user-1", "room-1", "primary", "Team meeting",
                        "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", null, null, null,
                        com.example.ai.services.PendingCalendarAction.STATUS_DECLINED));

        var response = controller().decline(user("user-1"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getBody()).containsEntry("status", "DECLINED");
    }

    @Test
    void declineWithoutActionIsBadRequest() {
        when(pendingCalendarActionService.complete(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Calendar action not found"));

        var response = controller().decline(user("user-1"), "abc");

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}