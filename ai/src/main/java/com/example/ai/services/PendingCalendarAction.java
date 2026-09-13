package com.example.ai.services;

import java.time.Instant;

public class PendingCalendarAction {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DECLINED = "DECLINED";

    private final String actionId;
    private final String userId;
    private final String roomId;
    private final String calendarId;
    private final String summary;
    private final String startTime;
    private final String endTime;
    private final String description;
    private final String location;
    private final Instant expiresAt;
    private String status;

    public PendingCalendarAction(String actionId, String userId, String roomId, String calendarId,
                                 String summary, String startTime, String endTime,
                                 String description, String location, Instant expiresAt, String status) {
        this.actionId = actionId;
        this.userId = userId;
        this.roomId = roomId;
        this.calendarId = calendarId;
        this.summary = summary;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
        this.location = location;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public String getActionId() {
        return actionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public String getSummary() {
        return summary;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}