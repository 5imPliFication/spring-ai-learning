-- Private rooms (hidden from discover, joined via invite link)
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE;

-- Reply-to messages
ALTER TABLE room_messages ADD COLUMN IF NOT EXISTS reply_to_id BIGINT NULL REFERENCES room_messages(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_room_messages_reply_to ON room_messages(reply_to_id);
