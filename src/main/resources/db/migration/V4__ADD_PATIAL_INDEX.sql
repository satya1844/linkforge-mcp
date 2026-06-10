CREATE INDEX idx_active_short_codes
ON links (short_code)
WHERE deleted_at IS NULL;