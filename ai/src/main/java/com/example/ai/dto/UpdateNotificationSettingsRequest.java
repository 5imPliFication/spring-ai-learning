package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateNotificationSettingsRequest(
    @NotBlank String mode
) {}
