package com.example.ai.services;

import com.example.ai.dto.UpdateProfileRequest;
import com.example.ai.dto.UserProfileResponse;
import com.example.ai.entity.User;
import com.example.ai.entity.UserLink;
import com.example.ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServicePublicProfileTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MediaService mediaService;

    private UserService userService;

    private User baseUser() {
        return User.builder()
                .id("user-1")
                .username("alice")
                .displayName("Alice")
                .avatarUrl("https://avatar.url/alice.jpg")
                .role("USER")
                .showBio(true)
                .showLocation(true)
                .showGender(true)
                .showPhone(true)
                .showLinks(true)
                .bio("Hello world")
                .location("Tokyo, JP")
                .gender("Female")
                .phone("+81-90-1234-5678")
                .links(new ArrayList<>(List.of(
                        UserLink.builder().id(1L).userId("user-1").label("GitHub").url("https://github.com/alice").position(0).build(),
                        UserLink.builder().id(2L).userId("user-1").label("Blog").url("https://alice.dev").position(1).build()
                )))
                .build();
    }

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, mediaService);
        lenient().when(userRepository.findById("user-1")).thenReturn(Optional.of(baseUser()));
    }

    @Test
    void publicProfileReturnsAllFieldsWhenAllVisible() {
        User user = baseUser();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse res = userService.getPublicProfile("user-1");
        assertThat(res.bio()).isEqualTo("Hello world");
        assertThat(res.location()).isEqualTo("Tokyo, JP");
        assertThat(res.gender()).isEqualTo("Female");
        assertThat(res.phone()).isEqualTo("+81-90-1234-5678");
        assertThat(res.links()).hasSize(2);
    }

    @Test
    void publicProfileHidesFieldsWhenToggledOff() {
        User user = baseUser();
        user.setShowBio(false);
        user.setShowLocation(false);
        user.setShowGender(false);
        user.setShowPhone(false);
        user.setShowLinks(false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse res = userService.getPublicProfile("user-1");
        assertThat(res.bio()).isNull();
        assertThat(res.location()).isNull();
        assertThat(res.gender()).isNull();
        assertThat(res.phone()).isNull();
        assertThat(res.links()).isEmpty();
    }

    @Test
    void publicProfileHidesIndividualFields() {
        User user = baseUser();
        user.setShowBio(true);
        user.setShowLocation(false);
        user.setShowGender(true);
        user.setShowPhone(false);
        user.setShowLinks(true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse res = userService.getPublicProfile("user-1");
        assertThat(res.bio()).isEqualTo("Hello world");
        assertThat(res.location()).isNull();
        assertThat(res.gender()).isEqualTo("Female");
        assertThat(res.phone()).isNull();
        assertThat(res.links()).hasSize(2);
    }

    @Test
    void publicProfileThrowsForDeletedUser() {
        User user = baseUser();
        user.setDeletedAt(java.time.Instant.now());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.getPublicProfile("user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicProfileThrowsForUnknownUser() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getPublicProfile("ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selfProfileReturnsAllFieldsRegardlessOfToggles() {
        User user = baseUser();
        user.setShowBio(false);
        user.setShowLinks(false);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        UserProfileResponse res = userService.getProfile(user);
        assertThat(res.bio()).isEqualTo("Hello world");
        assertThat(res.links()).hasSize(2);
    }

    @Test
    void updateProfileValidatesLinkUrlFormat() {
        User user = baseUser();
        UpdateProfileRequest req = new UpdateProfileRequest(
                null, null, null, null,
                null, null, null, null,
                List.of(new UpdateProfileRequest.LinkRequest("MySite", "ftp://bad.url")),
                null, null, null, null, null
        );

        assertThatThrownBy(() -> userService.updateProfile(user, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http://");
    }

    @Test
    void updateProfileRejectsMoreThanFiveLinks() {
        User user = baseUser();
        var links = List.of(
                new UpdateProfileRequest.LinkRequest("a", "https://a.com"),
                new UpdateProfileRequest.LinkRequest("b", "https://b.com"),
                new UpdateProfileRequest.LinkRequest("c", "https://c.com"),
                new UpdateProfileRequest.LinkRequest("d", "https://d.com"),
                new UpdateProfileRequest.LinkRequest("e", "https://e.com"),
                new UpdateProfileRequest.LinkRequest("f", "https://f.com")
        );
        UpdateProfileRequest req = new UpdateProfileRequest(
                null, null, null, null,
                null, null, null, null,
                links,
                null, null, null, null, null
        );

        assertThatThrownBy(() -> userService.updateProfile(user, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum 5");
    }

    @Test
    void updateProfileAllowsEmptyLinks() {
        User user = baseUser();
        UpdateProfileRequest req = new UpdateProfileRequest(
                null, null, null, null,
                "New bio", "London", "Non-binary", "+44-123",
                List.of(),
                true, true, true, true, true
        );

        UserProfileResponse res = userService.updateProfile(user, req);
        assertThat(res.bio()).isEqualTo("New bio");
        assertThat(res.location()).isEqualTo("London");
        assertThat(res.gender()).isEqualTo("Non-binary");
        assertThat(res.phone()).isEqualTo("+44-123");
        assertThat(res.links()).isEmpty();
    }

    @Test
    void searchUsersExcludesDeletedAndUsesPublicProjection() {
        User deleted = baseUser();
        deleted.setDeletedAt(java.time.Instant.now());
        User active = baseUser();
        active.setShowBio(false);
        active.setBio("should be hidden");
        when(userRepository.searchUsers("alice")).thenReturn(List.of(deleted, active));

        List<UserProfileResponse> results = userService.searchUsers("alice");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).bio()).isNull();
    }
}
