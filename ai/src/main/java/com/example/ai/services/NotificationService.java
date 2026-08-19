package com.example.ai.services;

import com.example.ai.dto.NotificationDto;
import com.example.ai.entity.Notification;
import com.example.ai.repository.NotificationRepository;
import com.example.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.notification.ttl-days:56}")
    private long ttlDays;

    public void create(String recipientId, String type, String title, String body, String senderId, String roomId) {
        if (recipientId == null || recipientId.isBlank()) return;

        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .type(type)
                .title(title)
                .body(body)
                .senderId(senderId)
                .roomId(roomId)
                .read(false)
                .build();
        Notification saved = notificationRepository.save(notification);

        messagingTemplate.convertAndSend("/topic/user/" + recipientId + "/notifications", toDto(saved));
    }

    public List<NotificationDto> getRecent(String userId, int limit) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(String userId, Long id) {
        notificationRepository.findById(id)
                .filter(n -> n.getRecipientId().equals(userId))
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 500))
                .forEach(n -> {
                    if (!n.isRead()) {
                        n.setRead(true);
                        notificationRepository.save(n);
                    }
                });
    }

    @Scheduled(cron = "${app.notification.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupOlderThan() {
        Instant cutoff = Instant.now().minus(ttlDays, ChronoUnit.DAYS);
        notificationRepository.deleteOlderThan(cutoff);
    }

    private NotificationDto toDto(Notification n) {
        String senderName = n.getSenderId() == null ? null
                : userRepository.findById(n.getSenderId()).map(u -> u.getDisplayName()).orElse(null);
        return new NotificationDto(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getSenderId(),
                senderName,
                n.getRoomId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}