package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record FriendRequestPayload(
    @NotBlank String friendId
) {}
