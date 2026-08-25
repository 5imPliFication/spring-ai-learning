-- Mentions stored per message for reliable notification + unread counting
CREATE TABLE IF NOT EXISTS message_mentions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES room_messages(id) ON DELETE CASCADE,
    room_id VARCHAR(36) NOT NULL,
    mentioned_user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_mentions UNIQUE (message_id, mentioned_user_id)
);

CREATE INDEX IF NOT EXISTS idx_message_mentions_user_recent
    ON message_mentions(mentioned_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_message_mentions_room
    ON message_mentions(room_id);

-- Per-user per-room notification preference + read cursor
CREATE TABLE IF NOT EXISTS room_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id VARCHAR(36) NOT NULL,
    mode VARCHAR(20) NOT NULL DEFAULT 'ALL',
    last_read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_room_notification_settings UNIQUE (user_id, room_id),
    CONSTRAINT ck_room_notification_settings_mode CHECK (mode IN ('ALL', 'MENTIONS_ONLY', 'MUTED'))
);

-- Speeds up per-room unread counting
CREATE INDEX IF NOT EXISTS idx_room_messages_room_created
    ON room_messages(room_id, created_at DESC);
