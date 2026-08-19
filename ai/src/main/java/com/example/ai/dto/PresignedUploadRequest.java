package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignedUploadRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull @Positive Long size
) {}