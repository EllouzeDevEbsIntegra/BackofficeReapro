-- Add customer_id and analysis columns to search_history_sync
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS customer_id BIGINT;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS normalized_filter VARCHAR(255);
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS is_closed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS closed_by VARCHAR(255) NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS closure_reason VARCHAR(500) NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS diagnostic_status VARCHAR(100) NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS diagnostic_comment TEXT NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS action_type VARCHAR(100) NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS linked_article_id BIGINT NULL;
ALTER TABLE search_history_sync ADD COLUMN IF NOT EXISTS closed_batch_id UUID NULL;

-- Create search_opportunity_decision table
CREATE TABLE IF NOT EXISTS search_opportunity_decision (
    id BIGSERIAL PRIMARY KEY,
    normalized_filter VARCHAR(255) NOT NULL,
    original_filter_example VARCHAR(255),
    decision_date TIMESTAMP NOT NULL DEFAULT now(),
    decided_by VARCHAR(255) NOT NULL,
    diagnostic_status VARCHAR(100) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    closure_reason VARCHAR(500),
    comment TEXT,
    linked_article_id BIGINT,
    closed_batch_id UUID NOT NULL,
    closed_rows_count INTEGER NOT NULL,
    first_search_date TIMESTAMP,
    last_search_date TIMESTAMP,
    distinct_customers_count INTEGER,
    total_attempts INTEGER,
    zero_result_count INTEGER,
    no_stock_count INTEGER
);

-- Create necessary indexes for performance
CREATE INDEX IF NOT EXISTS idx_lsh_closed_normalized ON search_history_sync (is_closed, normalized_filter);
CREATE INDEX IF NOT EXISTS idx_lsh_closed_creation_date ON search_history_sync (is_closed, creation_date);
CREATE INDEX IF NOT EXISTS idx_lsh_customer_id ON search_history_sync (customer_id);
CREATE INDEX IF NOT EXISTS idx_lsh_ext_id ON search_history_sync (customer_ext_id);
CREATE INDEX IF NOT EXISTS idx_lsh_results_count ON search_history_sync (results_count);
CREATE INDEX IF NOT EXISTS idx_lsh_stock_available ON search_history_sync (is_stock_available);

CREATE INDEX IF NOT EXISTS idx_decision_normalized_filter ON search_opportunity_decision (normalized_filter);
CREATE INDEX IF NOT EXISTS idx_decision_date ON search_opportunity_decision (decision_date);
