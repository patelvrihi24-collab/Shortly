CREATE TABLE click_events (
    id              BIGSERIAL PRIMARY KEY,
    url_id          BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    --EVERY CLICK BELONGS TO A LINK; DELETING THE LINK DELETES ITS CLICKS AUTOMATICALLY
    clicked_at      TIMESTAMP NOT NULL,               -- set by our code at click time (UTC), not at insert time
    browser         VARCHAR(32) NOT NULL,             -- "Chrome", "Safari", "Bot/Tool"...
    device          VARCHAR(16) NOT NULL,             -- "Desktop", "Mobile", "Tablet"...
    referrer_host   VARCHAR(255) NOT NULL,            -- "news.ycombinator.com", or "direct"
    visitor_hash    VARCHAR(32) NOT NULL              -- salted hash, never the raw IP
);

CREATE INDEX idx_click_events_url_time ON click_events (url_id, clicked_at);
-- Serves "clicks for THIS link in THIS time range" (our stats queries) AND makes cascade deletes fast,
-- because url_id is the first column. Postgres does not index foreign keys automatically.