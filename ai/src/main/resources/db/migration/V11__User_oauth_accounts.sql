-- OAuth connections between Azura users and external identity providers (Google).
CREATE TABLE user_oauth_accounts (
    id            BIGSERIAL PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    google_email  VARCHAR(254) NOT NULL,
    access_token  TEXT NOT NULL,
    refresh_token TEXT,
    token_type    VARCHAR(20),
    scope         TEXT,
    expires_at    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_oauth_accounts_user UNIQUE (user_id),
    CONSTRAINT fk_user_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_oauth_accounts_google_email ON user_oauth_accounts(google_email);