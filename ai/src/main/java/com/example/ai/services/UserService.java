package com.example.ai.services;

import com.example.ai.dto.UpdateProfileRequest;
import com.example.ai.dto.UserProfileResponse;
import com.example.ai.entity.User;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;

    public UserProfileResponse getProfile(User principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toProfileResponse(user);
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
                .map(this::toProfileResponse)
                .toList();
    }

    private UserProfileResponse toProfileResponse(User u) {
        return new UserProfileResponse(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getAvatarUrl(),
                u.getRole(),
                u.getCreatedAt(),
                u.getLastActiveAt()
        );
    }
}
