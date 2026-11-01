CREATE TABLE urls(
                     id BIGSERIAL PRIMARY KEY,
                     short_code VARCHAR(10) UNIQUE,
                     original_url TEXT NOT NULL,
                     created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                     expires_at TIMESTAMP,
                     click_count BIGINT NOT NULL DEFAULT 0
);
