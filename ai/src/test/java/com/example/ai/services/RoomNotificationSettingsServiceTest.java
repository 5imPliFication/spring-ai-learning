package com.example.ai.services;

import com.example.ai.entity.RoomNotificationSetting;
import com.example.ai.repository.RoomNotificationSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomNotificationSettingsServiceTest {

    @Mock
    private RoomNotificationSettingRepository settingsRepository;

    private RoomNotificationSettingsService service;

    private final String userId = "user-1";
    private final String roomId = "room-1";

    @BeforeEach
    void setUp() {
        service = new RoomNotificationSettingsService(settingsRepository);
        lenient().when(settingsRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.empty());
    }

    private void givenExistingMode(String mode) {
        RoomNotificationSetting row = RoomNotificationSetting.builder()
                .userId(userId)
                .roomId(roomId)
                .mode(mode)
                .lastReadAt(Instant.now())
                .build();
        when(settingsRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.of(row));
    }

    @Test
    void defaultModeIsAllWhenNoRowExists() {
        assertThat(service.getMode(userId, roomId)).isEqualTo("ALL");
    }

    @Test
    void allModeNotifiesEverything() {
        givenExistingMode("ALL");
        assertThat(service.shouldNotify(userId, roomId, true)).isTrue();
        assertThat(service.shouldNotify(userId, roomId, false)).isTrue();
    }

    @Test
    void mentionsOnlyNotifiesOnlyForMentions() {
        givenExistingMode("MENTIONS_ONLY");
        assertThat(service.shouldNotify(userId, roomId, true)).isTrue();
        assertThat(service.shouldNotify(userId, roomId, false)).isFalse();
    }

    @Test
    void mutedNeverNotifiesEvenForMentions() {
        givenExistingMode("MUTED");
        assertThat(service.shouldNotify(userId, roomId, true)).isFalse();
        assertThat(service.shouldNotify(userId, roomId, false)).isFalse();
    }

    @Test
    void setModeNormalizesCase() {
        service.setMode(userId, roomId, "mentions_only");
        ArgumentCaptor<RoomNotificationSetting> captor =
                ArgumentCaptor.forClass(RoomNotificationSetting.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("MENTIONS_ONLY");
    }

    @Test
    void setModeUpdatesExistingRowInPlace() {
        RoomNotificationSetting existing = RoomNotificationSetting.builder()
                .id(42L)
                .userId(userId)
                .roomId(roomId)
                .mode("ALL")
                .build();
        when(settingsRepository.findByUserIdAndRoomId(userId, roomId)).thenReturn(Optional.of(existing));

        service.setMode(userId, roomId, "MUTED");

        ArgumentCaptor<RoomNotificationSetting> captor =
                ArgumentCaptor.forClass(RoomNotificationSetting.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(42L);
        assertThat(captor.getValue().getMode()).isEqualTo("MUTED");
    }

    @Test
    void setModeRejectsUnknownValues() {
        assertThatThrownBy(() -> service.setMode(userId, roomId, "SOMETIMES"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setMode(userId, roomId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setMode(userId, roomId, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markRoomReadCreatesRowWhenNoneExists() {
        Instant before = Instant.now().minusSeconds(5);
        service.markRoomRead(userId, roomId);

        ArgumentCaptor<RoomNotificationSetting> captor =
                ArgumentCaptor.forClass(RoomNotificationSetting.class);
        verify(settingsRepository).save(captor.capture());
        RoomNotificationSetting saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getRoomId()).isEqualTo(roomId);
        assertThat(saved.getLastReadAt()).isAfter(before);
        assertThat(saved.getMode()).isEqualTo("ALL");
    }

    @Test
    void markRoomReadPreservesExistingMode() {
        givenExistingMode("MENTIONS_ONLY");

        service.markRoomRead(userId, roomId);

        ArgumentCaptor<RoomNotificationSetting> captor =
                ArgumentCaptor.forClass(RoomNotificationSetting.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("MENTIONS_ONLY");
        assertThat(captor.getValue().getLastReadAt()).isNotNull();
    }
}
