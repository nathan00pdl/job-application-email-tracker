-- Initial schema: the single table holding one row per Gmail message the daily
-- scan has looked at. This table is the source of truth; the Google Sheet is a
-- mirror of it (see ARCHITECTURE.md).
CREATE TABLE email_classifications (
    id                      BIGSERIAL PRIMARY KEY,

    -- Idempotency key. The UNIQUE constraint is what makes re-running the daily
    -- job safe: a message already processed cannot be inserted twice.
    gmail_message_id        VARCHAR(64)  NOT NULL UNIQUE,

    received_at             TIMESTAMPTZ  NOT NULL,
    sender_domain           VARCHAR(255) NOT NULL,
    platform                VARCHAR(100),

    -- Fields populated from the classifier output. Nullable because a message can
    -- be recorded as "looked at, not relevant" with nothing extracted from it.
    company                 VARCHAR(255),
    role_title              VARCHAR(255),
    -- VARCHAR rather than a native ENUM: new categories can be added without an
    -- ALTER TYPE migration. The allowed set is validated in the application layer.
    update_type             VARCHAR(50)  NOT NULL,
    summary                 TEXT,
    is_urgent               BOOLEAN      NOT NULL DEFAULT FALSE,

    -- The two independent classification signals, kept separately so disagreement
    -- between the cheap rule filter and the LLM stays auditable over time.
    matched_rule_filter     BOOLEAN      NOT NULL,
    llm_classified_relevant BOOLEAN      NOT NULL,
    has_disagreement        BOOLEAN      NOT NULL DEFAULT FALSE,
    included_in_digest      BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Filled in by hand in the Google Sheet (e.g. 'responded', 'ignored').
    manual_status           VARCHAR(50),

    -- NULL means "not yet mirrored into the Sheet"; the sync step selects on this.
    sheet_synced_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Supports the daily/periodic queries that slice records by arrival time.
CREATE INDEX idx_email_classifications_received_at
    ON email_classifications (received_at);

-- Supports pulling the rows that need manual review.
CREATE INDEX idx_email_classifications_has_disagreement
    ON email_classifications (has_disagreement);
