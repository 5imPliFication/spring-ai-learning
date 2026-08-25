package com.example.ai.services;

import com.example.ai.dto.UpdateProfileRequest;
import com.example.ai.dto.UserProfileResponse;
import com.example.ai.dto.UserProfileResponse.UserLinkResponse;
import com.example.ai.entity.User;
import com.example.ai.entity.UserLink;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;

    private static final int MAX_LINKS = 5;

    public UserProfileResponse getProfile(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toProfileResponse(user);
    }

    public UserProfileResponse getPublicProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("User not found");
        }
        return toPublicProfileResponse(user);
    }

    public UserProfileResponse updateProfile(User principal, UpdateProfileRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Invalid current password");
            }
            user.setPassword(passwordEncoder.encode(request.newPassword()));
        }

        // Profile fields
        user.setBio(request.bio() != null ? request.bio().trim() : null);
        user.setLocation(request.location() != null ? request.location().trim() : null);
        user.setGender(request.gender() != null ? request.gender().trim() : null);
        user.setPhone(request.phone() != null ? request.phone().trim() : null);

        // Visibility toggles
        if (request.showBio() != null) user.setShowBio(request.showBio());
        if (request.showLocation() != null) user.setShowLocation(request.showLocation());
        if (request.showGender() != null) user.setShowGender(request.showGender());
        if (request.showPhone() != null) user.setShowPhone(request.showPhone());
        if (request.showLinks() != null) user.setShowLinks(request.showLinks());

        // Links — replace-all strategy
        if (request.links() != null) {
            if (request.links().size() > MAX_LINKS) {
                throw new IllegalArgumentException("Maximum " + MAX_LINKS + " links allowed");
            }
            // Validate URLs
            for (UpdateProfileRequest.LinkRequest link : request.links()) {
                if (link.url() != null && !link.url().isBlank()) {
                    String url = link.url().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        throw new IllegalArgumentException("Links must start with http:// or https://");
                    }
                }
            }
            // Clear existing + add new
            user.getLinks().clear();
            int pos = 0;
            for (UpdateProfileRequest.LinkRequest link : request.links()) {
                if (link.label() != null && !link.label().isBlank() && link.url() != null && !link.url().isBlank()) {
                    user.getLinks().add(UserLink.builder()
                            .userId(user.getId())
                            .label(link.label().trim())
                            .url(link.url().trim())
                            .position(pos++)
                            .build());
                }
            }
        }

        userRepository.save(user);
        return toProfileResponse(user);
    }

    public void softDeleteOwnAccount(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    public UserProfileResponse uploadAvatar(User principal, MultipartFile file) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String newAvatarUrl = mediaService.uploadAvatar(file, user.getId());

        String oldAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(newAvatarUrl);
        userRepository.save(user);

        if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
            mediaService.deleteObject(oldAvatarUrl);
        }

        return toProfileResponse(user);
    }

    public List<UserProfileResponse> searchUsers(String query) {
        return userRepository.searchUsers(query).stream()
                .filter(u -> u.getDeletedAt() == null)
                .map(this::toPublicProfileResponse)
                .toList();
    }

    private UserProfileResponse toProfileResponse(User u) {
        List<UserLinkResponse> links = u.getLinks().stream()
                .sorted(Comparator.comparingInt(UserLink::getPosition))
                .map(l -> new UserLinkResponse(l.getId(), l.getLabel(), l.getUrl(), l.getPosition()))
                .toList();
        return new UserProfileResponse(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole(),
                u.getCreatedAt(),
                u.getLastActiveAt(),
                u.getBio(),
                u.getLocation(),
                u.getGender(),
                u.getPhone(),
                links
        );
    }

    private UserProfileResponse toPublicProfileResponse(User u) {
        List<UserLinkResponse> links = u.getLinks().stream()
                .sorted(Comparator.comparingInt(UserLink::getPosition))
                .map(l -> new UserLinkResponse(l.getId(), l.getLabel(), l.getUrl(), l.getPosition()))
                .toList();
        return new UserProfileResponse(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole(),
                u.getCreatedAt(),
                u.getLastActiveAt(),
                Boolean.TRUE.equals(u.getShowBio()) ? u.getBio() : null,
                Boolean.TRUE.equals(u.getShowLocation()) ? u.getLocation() : null,
                Boolean.TRUE.equals(u.getShowGender()) ? u.getGender() : null,
                Boolean.TRUE.equals(u.getShowPhone()) ? u.getPhone() : null,
                Boolean.TRUE.equals(u.getShowLinks()) ? links : List.of()
        );
    }
}
