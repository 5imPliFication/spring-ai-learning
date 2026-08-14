package com.example.ai.dto;

public record AdminDashboardStats(
    long dailyMessages,
    long weeklyMessages,
    long monthlyMessages,
    long activeUsersCount,
    long onlineUsersCount,
    long totalRoomsCount,
    long dailyTokensUsed,
    long weeklyTokensUsed,
    long monthlyTokensUsed
) {}
