package com.example.ai.dto;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
    String id,
    String username,
    String displayName,
    String avatarUrl,
    String role,
    Instant createdAt,
    Instant lastActiveAt,
    // Profile fields (null = not visible)
    String bio,
    String location,
    String gender,
    String phone,
    List<UserLinkResponse> links
) {
    public record UserLinkResponse(
        Long id,
        String label,
        String url,
        Integer position
    ) {}
}
