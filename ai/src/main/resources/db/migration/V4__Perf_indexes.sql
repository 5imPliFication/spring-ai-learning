-- Performance indexes
CREATE INDEX IF NOT EXISTS idx_room_members_user ON room_members(user_id);
CREATE INDEX IF NOT EXISTS idx_rooms_created_by ON rooms(created_by) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_rooms_created_at ON rooms(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_room_messages_sender ON room_messages(sender_id);