CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,               -- auto-incrementing id
                       email         VARCHAR(255) NOT NULL UNIQUE,        -- login name; UNIQUE also creates an index for fast lookups
                       password_hash VARCHAR(100) NOT NULL,               -- BCrypt hash (60 chars); NEVER the real password
                       created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);