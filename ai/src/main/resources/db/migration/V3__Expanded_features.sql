-- Alter users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP WITH TIME ZONE NULL;

-- Alter rooms table
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NULL;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL;

-- Alter room_members table
ALTER TABLE room_members ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';

-- Alter room_messages table
ALTER TABLE room_messages ADD COLUMN IF NOT EXISTS prompt_tokens INT DEFAULT 0;
ALTER TABLE room_messages ADD COLUMN IF NOT EXISTS completion_tokens INT DEFAULT 0;
ALTER TABLE room_messages ADD COLUMN IF NOT EXISTS total_tokens INT DEFAULT 0;
ALTER TABLE room_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE NULL;

-- Create friends table
CREATE TABLE IF NOT EXISTS friends (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_friend UNIQUE (user_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friends_user ON friends(user_id, status);

-- Seed default System Admin user (password: admin123)
INSERT INTO users (id, username, password, display_name, role)
VALUES (
    'admin-1',
    'admin',
    '$2a$10$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquz.cgYkG37F6z/c/N1I6',
    'System Admin',
    'ADMIN'
) ON CONFLICT (username) DO NOTHING;
