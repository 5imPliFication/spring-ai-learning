package com.example.ai.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100) String displayName,
    @Size(max = 500) String avatarUrl,
    String currentPassword,
    @Size(min = 6, max = 100) String newPassword
) {}
