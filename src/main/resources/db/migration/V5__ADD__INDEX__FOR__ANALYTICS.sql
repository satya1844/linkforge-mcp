CREATE INDEX idx_clicks_link_id_time
ON link_clicks (link_id, clicked_at DESC);