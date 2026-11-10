ALTER TABLE urls
    ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;
-- Nullable: anonymous links have no owner. REFERENCES = foreign key: must point at a real user.
-- ON DELETE CASCADE: if a user is deleted, their links go with them.

CREATE INDEX idx_urls_user_id ON urls(user_id);
-- Postgres does NOT auto-index foreign keys (unlike PRIMARY KEY/UNIQUE). "My links" filters by user_id,
-- so without this index every listing would scan the whole table.