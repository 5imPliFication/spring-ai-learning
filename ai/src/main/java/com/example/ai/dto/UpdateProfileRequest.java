package com.example.ai.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProfileRequest(
    @Size(max = 100) String displayName,
    @Size(max = 500) String avatarUrl,
    String currentPassword,
    @Size(min = 6, max = 100) String newPassword,
    // Profile fields
    @Size(max = 500) String bio,
    @Size(max = 100) String location,
    @Size(max = 50) String gender,
    @Size(max = 20) String phone,
    List<LinkRequest> links,
    // Visibility toggles
    Boolean showBio,
    Boolean showLocation,
    Boolean showGender,
    Boolean showPhone,
    Boolean showLinks
) {
    public record LinkRequest(
        @Size(max = 50) String label,
        @Size(max = 500) String url
    ) {}
}
