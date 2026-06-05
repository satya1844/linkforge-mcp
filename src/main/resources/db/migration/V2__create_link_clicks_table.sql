CREATE TABLE link_clicks (
    id          BIGSERIAL PRIMARY KEY,
    link_id     BIGINT REFERENCES links(id),
    clicked_at  TIMESTAMP DEFAULT NOW(),
    user_agent  TEXT,
    referrer    TEXT,
    ip_address  VARCHAR(45)
);