package com.example.ai.dto;

public record CalendarActionCard(
        String actionId,
        String userId,
        String calendarId,
        String summary,
        String startTime,
        String endTime,
        String description,
        String location,
        String status
) {}