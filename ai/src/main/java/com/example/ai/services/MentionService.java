package com.example.ai.services;

import com.example.ai.entity.MessageMention;
import com.example.ai.entity.RoomMessage;
import com.example.ai.entity.User;
import com.example.ai.repository.MessageMentionRepository;
import com.example.ai.repository.RoomMemberRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_.-]+)");

    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final MessageMentionRepository messageMentionRepository;

    /**
     * Resolves @username tokens in the content against current members of the room.
     * Matching is case-insensitive on the username; duplicates and the sender are removed.
     */
    public Set<String> extractMentionedUserIds(String roomId, String senderId, String content) {
        if (content == null || content.isBlank()) return Set.of();

        Set<String> memberIds = roomMemberRepository.findByRoomId(roomId).stream()
                .map(m -> m.getUserId())
                .collect(Collectors.toSet());
        if (memberIds.isEmpty()) return Set.of();
        List<User> members = userRepository.findAllById(memberIds);

        if (members.isEmpty()) return Set.of();

        Map<String, String> userIdByUsername = members.stream()
                .collect(Collectors.toMap(u -> u.getUsername().toLowerCase(), User::getId, (a, b) -> a));

        Set<String> mentioned = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String token = matcher.group(1).toLowerCase();
            String userId = userIdByUsername.get(token);
            if (userId != null && !userId.equals(senderId)) {
                mentioned.add(userId);
            }
        }
        return mentioned;
    }

    @Transactional
    public void saveMentions(RoomMessage message, Collection<String> mentionedUserIds) {
        if (message == null || message.getId() == null || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }
        for (String userId : mentionedUserIds) {
            if (!messageMentionRepository.existsByMessageIdAndMentionedUserId(message.getId(), userId)) {
                messageMentionRepository.save(MessageMention.builder()
                        .messageId(message.getId())
                        .roomId(message.getRoomId())
                        .mentionedUserId(userId)
                        .build());
            }
        }
    }

    public Map<Long, Set<String>> getMentionsForMessages(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Map.of();
        return messageMentionRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(MessageMention::getMessageId,
                        Collectors.mapping(MessageMention::getMentionedUserId, Collectors.toSet())));
    }
}
