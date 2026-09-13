package com.example.ai.services;

import com.example.ai.dto.CalendarActionCard;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingCalendarActionServiceTest {

    private final PendingCalendarActionService service = new PendingCalendarActionService();

    @Test
    void stagedActionIsPendingAndOwnedByRequester() {
        PendingCalendarAction action = service.stage(
                "user-1", "room-1", "primary", "Meeting", "2026-09-14T10:00:00Z",
                "2026-09-14T11:00:00Z", "desc", null);

        assertThat(action.getActionId()).isNotBlank();
        assertThat(action.getStatus()).isEqualTo(PendingCalendarAction.STATUS_PENDING);
        assertThat(service.getOwnedPending(action.getActionId(), "user-1")).isPresent();
        assertThat(service.getOwnedPending(action.getActionId(), "user-2")).isEmpty();
        assertThat(service.cardFor(action.getActionId())).contains(
                new CalendarActionCard(action.getActionId(), "user-1", "primary", "Meeting",
                        "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", "desc", null,
                        PendingCalendarAction.STATUS_PENDING));
    }

    @Test
    void blankCalendarIdDefaultsToPrimary() {
        PendingCalendarAction action = service.stage(
                "user-1", "room-1", "", "Meeting", "2026-09-14T10:00:00Z",
                "2026-09-14T11:00:00Z", null, null);

        assertThat(action.getCalendarId()).isEqualTo("primary");
    }

    @Test
    void completedActionBecomesUnavailable() {
        PendingCalendarAction action = service.stage(
                "user-1", "room-1", "primary", "Meeting", "2026-09-14T10:00:00Z",
                "2026-09-14T11:00:00Z", null, null);

        PendingCalendarAction complete = service.complete(action.getActionId(), "user-1",
                PendingCalendarAction.STATUS_APPROVED);

        assertThat(complete.getStatus()).isEqualTo(PendingCalendarAction.STATUS_APPROVED);
        assertThat(service.getOwnedPending(action.getActionId(), "user-1")).isEmpty();
        assertThat(service.cardFor(action.getActionId())).isEmpty();
    }

    @Test
    void completingTwiceOrAsOtherUserThrows() {
        PendingCalendarAction action = service.stage(
                "user-1", "room-1", "primary", "Meeting", "2026-09-14T10:00:00Z",
                "2026-09-14T11:00:00Z", null, null);

        assertThatThrownBy(() -> service.complete(action.getActionId(), "user-2",
                PendingCalendarAction.STATUS_APPROVED)).isInstanceOf(IllegalArgumentException.class);

        service.complete(action.getActionId(), "user-1", PendingCalendarAction.STATUS_APPROVED);

        assertThatThrownBy(() -> service.complete(action.getActionId(), "user-1",
                PendingCalendarAction.STATUS_DECLINED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredActionIsTreatedAsMissing() throws Exception {
        PendingCalendarActionService expiredService = new PendingCalendarActionService();
        PendingCalendarAction action = expiredService.stage(
                "user-1", "room-1", "primary", "Meeting", "2026-09-14T10:00:00Z",
                "2026-09-14T11:00:00Z", null, null);
        java.lang.reflect.Field expiresAt = PendingCalendarAction.class.getDeclaredField("expiresAt");
        expiresAt.setAccessible(true);
        expiresAt.set(action, java.time.Instant.now().minusSeconds(1));

        assertThat(expiredService.getPending(action.getActionId())).isEmpty();
        assertThat(expiredService.cardFor(action.getActionId())).isEmpty();
    }
}