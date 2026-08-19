-- Drop old tables
DROP TABLE IF EXISTS spring_ai_chat_memory;
DROP TABLE IF EXISTS chats;

-- Users table
CREATE TABLE users (
    id           VARCHAR(36) PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    avatar_url   VARCHAR(500),
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Rooms table (supports DIRECT and GROUP)
CREATE TABLE rooms (
    id         VARCHAR(36) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL DEFAULT 'GROUP',
    created_by VARCHAR(36)  REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Room Members table
CREATE TABLE room_members (
    id        BIGSERIAL PRIMARY KEY,
    room_id   VARCHAR(36) NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id   VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_room_user UNIQUE (room_id, user_id)
);

-- Messages table
CREATE TABLE room_messages (
    id           BIGSERIAL PRIMARY KEY,
    room_id      VARCHAR(36) NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    sender_id    VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content      TEXT,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    media_url    VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_room_messages_room_created ON room_messages(room_id, created_at DESC);

-- Seed Azura AI system user
INSERT INTO users (id, username, password, display_name, role)
VALUES ('ai-bot', 'azura', 'SYSTEM_ACCOUNT_NO_LOGIN', 'Azura', 'SYSTEM');
