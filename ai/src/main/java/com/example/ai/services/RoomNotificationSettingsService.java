package com.example.ai.services;

import com.example.ai.entity.RoomNotificationSetting;
import com.example.ai.repository.RoomNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoomNotificationSettingsService {

    private final RoomNotificationSettingRepository settingsRepository;

    public String getMode(String userId, String roomId) {
        return settingsRepository.findByUserIdAndRoomId(userId, roomId)
                .map(RoomNotificationSetting::getMode)
                .orElse(RoomNotificationSetting.MODE_ALL);
    }

    public Map<String, String> getModesForUser(String userId, List<String> roomIds) {
        Map<String, String> modes = new HashMap<>();
        if (roomIds == null || roomIds.isEmpty()) return modes;
        settingsRepository.findByUserId(userId).stream()
                .filter(s -> roomIds.contains(s.getRoomId()))
                .forEach(s -> modes.put(s.getRoomId(), s.getMode()));
        return modes;
    }

    @Transactional
    public void setMode(String userId, String roomId, String mode) {
        String normalized = normalizeMode(mode);
        RoomNotificationSetting setting = settingsRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> RoomNotificationSetting.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .build());
        setting.setMode(normalized);
        settingsRepository.save(setting);
    }

    @Transactional
    public Instant markRoomRead(String userId, String roomId) {
        RoomNotificationSetting setting = settingsRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> RoomNotificationSetting.builder()
                        .userId(userId)
                        .roomId(roomId)
                        .mode(RoomNotificationSetting.MODE_ALL)
                        .build());
        Instant now = Instant.now();
        setting.setLastReadAt(now);
        settingsRepository.save(setting);
        return now;
    }

    /**
     * Whether a non-mention message should produce a notification for this user in this room.
     */
    public boolean shouldNotify(String userId, String roomId, boolean isMention) {
        String mode = getMode(userId, roomId);
        return switch (mode) {
            case RoomNotificationSetting.MODE_MUTED -> false;
            case RoomNotificationSetting.MODE_MENTIONS_ONLY -> isMention;
            default -> true;
        };
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("Notification mode is required");
        }
        String upper = mode.trim().toUpperCase();
        if (!RoomNotificationSetting.MODE_ALL.equals(upper)
                && !RoomNotificationSetting.MODE_MENTIONS_ONLY.equals(upper)
                && !RoomNotificationSetting.MODE_MUTED.equals(upper)) {
            throw new IllegalArgumentException("Invalid notification mode: " + mode);
        }
        return upper;
    }
}
