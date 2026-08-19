package com.example.ai.dto;

import jakarta.validation.constraints.Size;

public record UpdateRoomRequest(
    @Size(max = 100) String name,
    String password
) {}