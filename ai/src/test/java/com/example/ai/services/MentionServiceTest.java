package com.example.ai.services;

import com.example.ai.entity.RoomMember;
import com.example.ai.entity.User;
import com.example.ai.repository.MessageMentionRepository;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageMentionRepository messageMentionRepository;

    private MentionService mentionService;

    private final User alice = User.builder().id("alice-id").username("alice").displayName("Alice").build();
    private final User bob = User.builder().id("bob-id").username("bob.smith").displayName("Bob Smith").build();
    private final User carol = User.builder().id("carol-id").username("carol").displayName("Carol").build();

    @BeforeEach
    void setUp() {
        mentionService = new MentionService(roomMemberRepository, userRepository, messageMentionRepository);
        RoomMember membership = RoomMember.builder().roomId("room-1").userId(alice.getId()).build();
        lenient().when(roomMemberRepository.findByRoomId(eq("room-1"))).thenReturn(List.of(membership,
                RoomMember.builder().roomId("room-1").userId(bob.getId()).build(),
                RoomMember.builder().roomId("room-1").userId(carol.getId()).build()));
        lenient().when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(alice, bob, carol));
    }

    @Test
    void extractsSingleMention() {
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "sender", "hey @alice check this");
        assertThat(result).containsExactly("alice-id");
    }

    @Test
    void matchingIsCaseInsensitive() {
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "sender", "@ALICE hello");
        assertThat(result).containsExactly("alice-id");
    }

    @Test
    void extractsMultipleDistinctMentions() {
        Set<String> result =
                mentionService.extractMentionedUserIds("room-1", "sender", "@alice and @bob.smith and @alice again");
        assertThat(result).containsExactlyInAnyOrder("alice-id", "bob-id");
    }

    @Test
    void senderIsNeverMentionedBySelf() {
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "alice-id", "@alice look at me");
        assertThat(result).isEmpty();
    }

    @Test
    void unknownUsernamesAreIgnored() {
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "sender", "@ghost @nobody here");
        assertThat(result).isEmpty();
    }

    @Test
    void prefixTokensDoNotMatch() {
        // "alicia" starts with member username "alice" but is a different token
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "sender", "@alicia hi");
        assertThat(result).isEmpty();
    }

    @Test
    void nullOrBlankContentYieldsNoMentions() {
        assertThat(mentionService.extractMentionedUserIds("room-1", "sender", null)).isEmpty();
        assertThat(mentionService.extractMentionedUserIds("room-1", "sender", "   ")).isEmpty();
    }

    @Test
    void emailLikeTokenStillMatchesUsernamePrefix() {
        // "@bob" followed by ".smith" resolves because the token regex captures the dot
        Set<String> result = mentionService.extractMentionedUserIds("room-1", "sender", "ping @bob.smith now");
        assertThat(result).containsExactly("bob-id");
    }
}
