-- The classifier no longer produces two independent answers.
--
-- Those four columns existed to support a design where a cheap rule filter decided which
-- emails were worth sending to a language model, and the two answers were kept apart so
-- that a disagreement between them stayed visible. With no model to pay for, there is no
-- reason to run a cheap filter first, and no second opinion to compare against.
--
-- Only emails that are about a job application are stored now, so a column saying so
-- would be true on every row.
ALTER TABLE email_classifications
    DROP COLUMN matched_rule_filter,
    DROP COLUMN llm_classified_relevant,
    DROP COLUMN has_disagreement,
    DROP COLUMN included_in_digest;

-- idx_email_classifications_has_disagreement is dropped with its column: PostgreSQL
-- removes any index that depends on a column being dropped.
