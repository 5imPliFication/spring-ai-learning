package com.example.ai.dto;

public record AuthResponse(
    String token,
    String userId,
    String username,
    String displayName
) {}
