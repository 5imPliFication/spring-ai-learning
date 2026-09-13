package com.example.ai.services;

import com.example.ai.dto.CalendarActionCard;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of calendar actions awaiting user approval. Actions expire after
 * {@link #TTL} and are evicted lazily (on lookup) and by a periodic sweep.
 */
@Service
public class PendingCalendarActionService {

    public static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, PendingCalendarAction> actions = new ConcurrentHashMap<>();

    public PendingCalendarAction stage(String userId, String roomId, String calendarId, String summary,
                                       String startTime, String endTime, String description, String location) {
        String actionId = UUID.randomUUID().toString();
        PendingCalendarAction action = new PendingCalendarAction(
                actionId,
                userId,
                roomId,
                calendarId != null && !calendarId.isBlank() ? calendarId : "primary",
                summary,
                startTime,
                endTime,
                description,
                location,
                Instant.now().plus(TTL),
                PendingCalendarAction.STATUS_PENDING);
        actions.put(actionId, action);
        return action;
    }

    public Optional<PendingCalendarAction> getPending(String actionId) {
        return lookup(actionId)
                .filter(a -> PendingCalendarAction.STATUS_PENDING.equals(a.getStatus()));
    }

    public Optional<PendingCalendarAction> getOwnedPending(String actionId, String userId) {
        return getPending(actionId)
                .filter(a -> userId != null && userId.equals(a.getUserId()));
    }

    public Optional<CalendarActionCard> cardFor(String actionId) {
        return lookup(actionId).map(a -> new CalendarActionCard(
                a.getActionId(),
                a.getUserId(),
                a.getCalendarId(),
                a.getSummary(),
                a.getStartTime(),
                a.getEndTime(),
                a.getDescription(),
                a.getLocation(),
                a.getStatus()));
    }

    /**
     * Marks the action as resolved (approved/declined) and removes it from the store.
     *
     * @throws IllegalArgumentException when the action is missing, expired, already resolved, or owned by another user
     */
    public PendingCalendarAction complete(String actionId, String userId, String newStatus) {
        PendingCalendarAction action = getOwnedPending(actionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Calendar action not found, expired, or already resolved"));
        actions.remove(actionId);
        action.setStatus(newStatus);
        return action;
    }

    private Optional<PendingCalendarAction> lookup(String actionId) {
        if (actionId == null) {
            return Optional.empty();
        }
        PendingCalendarAction action = actions.get(actionId);
        if (action == null) {
            return Optional.empty();
        }
        if (isExpired(action)) {
            actions.remove(actionId);
            return Optional.empty();
        }
        return Optional.of(action);
    }

    private boolean isExpired(PendingCalendarAction action) {
        return action.getExpiresAt() != null && Instant.now().isAfter(action.getExpiresAt());
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        actions.values().removeIf(this::isExpired);
    }
}