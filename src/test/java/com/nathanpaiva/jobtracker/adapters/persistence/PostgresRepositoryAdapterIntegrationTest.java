package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the persistence adapter against a real PostgreSQL.
 *
 * <p>Everything here goes through {@link com.nathanpaiva.jobtracker.ports.PersistencePort},
 * never through the repository or the entity, because the port is the only thing the
 * rest of the application can see. If a test had to reach past it, the boundary would
 * not be holding.
 *
 * <p>The reads, on the other hand, use raw SQL on purpose. Asking the adapter to read
 * back what the adapter just wrote would pass even if the mapping put every value in
 * the wrong column. Reading the columns directly is what actually checks the mapping.
 */
class PostgresRepositoryAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-24T10:15:30Z");

    @Autowired
    private com.nathanpaiva.jobtracker.ports.PersistencePort persistence;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAClassificationWithEveryValueInTheRightColumn() {
        persistence.save(new EmailClassification(
                "gmail-id-full-row", RECEIVED_AT, "greenhouse.io", "Greenhouse",
                "Acme Corp", "Backend Engineer", UpdateType.INTERVIEW_INVITE,
                "Convite para entrevista técnica", true, true, true));

        Map<String, Object> row = rowFor("gmail-id-full-row");

        assertThat(row.get("received_at")).isNotNull();
        assertThat(row.get("sender_domain")).isEqualTo("greenhouse.io");
        assertThat(row.get("platform")).isEqualTo("Greenhouse");
        assertThat(row.get("company")).isEqualTo("Acme Corp");
        assertThat(row.get("role_title")).isEqualTo("Backend Engineer");
        assertThat(row.get("summary")).isEqualTo("Convite para entrevista técnica");
        assertThat(row.get("is_urgent")).isEqualTo(true);
    }

    /**
     * The column is text, and the value stored is the constant's name. Storing the
     * ordinal instead would mean reordering the enum silently changed every saved row.
     */
    @Test
    void storesTheUpdateTypeByName() {
        persistence.save(classificationWith("gmail-id-update-type", true, true));

        assertThat(rowFor("gmail-id-update-type").get("update_type")).isEqualTo("OTHER");
    }

    /**
     * The domain works these two out rather than holding them, so this is the point
     * where the rule turns into stored data. If the adapter forgot to ask, both columns
     * would quietly keep their FALSE default.
     */
    @Test
    void writesTheDerivedFlagsAsTheDomainWorksThemOut() {
        persistence.save(classificationWith("gmail-id-disagreement", true, false));

        Map<String, Object> row = rowFor("gmail-id-disagreement");

        assertThat(row.get("matched_rule_filter")).isEqualTo(true);
        assertThat(row.get("llm_classified_relevant")).isEqualTo(false);
        assertThat(row.get("has_disagreement")).isEqualTo(true);
        assertThat(row.get("included_in_digest")).isEqualTo(false);
    }

    @Test
    void leavesTheColumnsTheApplicationDoesNotOwnToTheDatabase() {
        persistence.save(classificationWith("gmail-id-db-owned", true, true));

        Map<String, Object> row = rowFor("gmail-id-db-owned");

        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("manual_status")).isNull();
        assertThat(row.get("sheet_synced_at")).isNull();
    }

    @Test
    void reportsWhetherAnEmailWasAlreadyProcessed() {
        persistence.save(classificationWith("gmail-id-already-seen", true, true));

        assertThat(persistence.existsByGmailMessageId("gmail-id-already-seen")).isTrue();
        assertThat(persistence.existsByGmailMessageId("gmail-id-never-seen")).isFalse();
    }

    /**
     * The check above is what avoids the cost of classifying an email twice. This is
     * what makes it safe anyway if two runs overlap and both pass the check before
     * either saves.
     */
    @Test
    void refusesToSaveTheSameEmailTwice() {
        persistence.save(classificationWith("gmail-id-duplicate", true, true));

        assertThatThrownBy(() -> persistence.save(classificationWith("gmail-id-duplicate", true, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static EmailClassification classificationWith(
            String gmailMessageId, boolean matchedRuleFilter, boolean llmClassifiedRelevant) {

        return new EmailClassification(
                gmailMessageId, RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false, matchedRuleFilter, llmClassifiedRelevant);
    }

    private Map<String, Object> rowFor(String gmailMessageId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM email_classifications WHERE gmail_message_id = ?", gmailMessageId);
    }
}
