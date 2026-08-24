package com.nathanpaiva.jobtracker;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies V1__create_email_classifications.sql against a real PostgreSQL.
 *
 * <p>These assertions are about behaviour the application depends on but does not
 * implement itself — most importantly the unique constraint on {@code gmail_message_id},
 * which is the entire basis for the daily job being safe to re-run. An in-memory
 * database would not prove any of it, since it would not run this same DDL.
 */
class EmailClassificationsSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesTheInitialMigration() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);

        assertThat(appliedVersions).containsExactly("1");
    }

    @Test
    void createsTheExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'email_classifications'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "gmail_message_id", "received_at", "sender_domain", "platform",
                "company", "role_title", "update_type", "summary", "is_urgent",
                "matched_rule_filter", "llm_classified_relevant", "has_disagreement",
                "included_in_digest", "manual_status", "sheet_synced_at", "created_at");
    }

    @Test
    void rejectsADuplicateGmailMessageId() {
        insertClassification("gmail-message-id-duplicate");

        assertThatThrownBy(() -> insertClassification("gmail-message-id-duplicate"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void appliesTheDeclaredColumnDefaults() {
        insertClassification("gmail-message-id-defaults");

        var row = jdbcTemplate.queryForMap("""
                SELECT is_urgent, has_disagreement, included_in_digest, sheet_synced_at, created_at
                FROM email_classifications
                WHERE gmail_message_id = 'gmail-message-id-defaults'
                """);

        assertThat(row.get("is_urgent")).isEqualTo(false);
        assertThat(row.get("has_disagreement")).isEqualTo(false);
        assertThat(row.get("included_in_digest")).isEqualTo(false);
        // NULL is what the Sheet sync step selects on to find rows not mirrored yet.
        assertThat(row.get("sheet_synced_at")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    /** Inserts a row supplying only the NOT NULL columns that have no default. */
    private void insertClassification(String gmailMessageId) {
        jdbcTemplate.update("""
                INSERT INTO email_classifications (
                    gmail_message_id, received_at, sender_domain, update_type,
                    matched_rule_filter, llm_classified_relevant)
                VALUES (?, now(), ?, ?, ?, ?)
                """, gmailMessageId, "greenhouse.io", "INTERVIEW_INVITE", true, true);
    }
}
