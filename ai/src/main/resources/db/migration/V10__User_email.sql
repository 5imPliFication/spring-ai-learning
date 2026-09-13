ALTER TABLE users ADD COLUMN email VARCHAR(254);

CREATE UNIQUE INDEX uk_users_email ON users(email) WHERE email IS NOT NULL;