CREATE TABLE links (
    id          BIGSERIAL PRIMARY KEY,
    short_code  VARCHAR(20) UNIQUE NOT NULL,
    original_url TEXT NOT NULL,
    custom_alias BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW(),
    expires_at  TIMESTAMP,
    deleted_at  TIMESTAMP
);
