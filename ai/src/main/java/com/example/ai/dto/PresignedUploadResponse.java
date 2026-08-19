package com.example.ai.dto;

public record PresignedUploadResponse(
        String uploadUrl,
        String mediaUrl,
        String messageType  // IMAGE, AUDIO, FILE
) {}