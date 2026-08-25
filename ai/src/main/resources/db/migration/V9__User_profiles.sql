-- Profile fields
ALTER TABLE users ADD COLUMN bio       VARCHAR(500);
ALTER TABLE users ADD COLUMN location  VARCHAR(100);
ALTER TABLE users ADD COLUMN gender    VARCHAR(50);
ALTER TABLE users ADD COLUMN phone     VARCHAR(20);

-- Visibility toggles (default shown)
ALTER TABLE users ADD COLUMN show_bio      BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN show_location BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN show_gender   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN show_phone    BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN show_links    BOOLEAN NOT NULL DEFAULT TRUE;

-- External links
CREATE TABLE user_links (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(50)  NOT NULL,
    url        VARCHAR(500) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_links_user ON user_links(user_id);
