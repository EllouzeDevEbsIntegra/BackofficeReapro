CREATE TABLE IF NOT EXISTS search_exclusions (
    id BIGSERIAL PRIMARY KEY,
    normalized_filter VARCHAR(255) UNIQUE NOT NULL,
    reason VARCHAR(500),
    created_by VARCHAR(255),
    created_at TIMESTAMP
);
