package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProtectedRoomRequest(
    @NotBlank @Size(max = 100) String name,
    String password
) {}
