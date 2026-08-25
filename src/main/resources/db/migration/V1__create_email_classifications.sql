-- First migration: one row per Gmail message the daily scan has looked at.
-- This table is the source of truth. The Google Sheet is a copy of it
-- (see ARCHITECTURE.md).
CREATE TABLE email_classifications (
    id                      BIGSERIAL PRIMARY KEY,

    -- Idempotency key. The UNIQUE constraint is what makes it safe to run the
    -- daily job again: an email already processed cannot be inserted twice.
    gmail_message_id        VARCHAR(64)  NOT NULL UNIQUE,

    received_at             TIMESTAMPTZ  NOT NULL,
    sender_domain           VARCHAR(255) NOT NULL,
    platform                VARCHAR(100),

    -- Filled in from the classifier output. These can be null because an email can
    -- be saved as "looked at, nothing taken from it".
    company                 VARCHAR(255),
    role_title              VARCHAR(255),
    -- VARCHAR instead of a native ENUM, so a new category never needs an ALTER TYPE
    -- migration. The list of allowed values is checked in the application instead.
    update_type             VARCHAR(50)  NOT NULL,
    summary                 TEXT,
    is_urgent               BOOLEAN      NOT NULL DEFAULT FALSE,

    -- The two classification signals, kept apart so we can still see when the cheap
    -- rule filter and the LLM disagree.
    matched_rule_filter     BOOLEAN      NOT NULL,
    llm_classified_relevant BOOLEAN      NOT NULL,
    has_disagreement        BOOLEAN      NOT NULL DEFAULT FALSE,
    included_in_digest      BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Filled in by hand in the Google Sheet (e.g. 'responded', 'ignored').
    manual_status           VARCHAR(50),

    -- NULL means "not copied to the Sheet yet". The sync step looks for these rows.
    sheet_synced_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Speeds up the queries that filter rows by arrival time.
CREATE INDEX idx_email_classifications_received_at
    ON email_classifications (received_at);

-- Speeds up finding the rows that need manual review.
CREATE INDEX idx_email_classifications_has_disagreement
    ON email_classifications (has_disagreement);
