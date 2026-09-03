package com.nathanpaiva.jobtracker.adapters.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.nathanpaiva.jobtracker.AbstractPostgresIntegrationTest;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
                "Convite para entrevista técnica", true));

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
        persistence.save(classificationWith("gmail-id-update-type"));

        assertThat(rowFor("gmail-id-update-type").get("update_type")).isEqualTo("OTHER");
    }

    @Test
    void leavesTheColumnsTheApplicationDoesNotOwnToTheDatabase() {
        persistence.save(classificationWith("gmail-id-db-owned"));

        Map<String, Object> row = rowFor("gmail-id-db-owned");

        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("manual_status")).isNull();
        assertThat(row.get("sheet_synced_at")).isNull();
    }

    @Test
    void reportsWhetherAnEmailWasAlreadyProcessed() {
        persistence.save(classificationWith("gmail-id-already-seen"));

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
        persistence.save(classificationWith("gmail-id-duplicate"));

        assertThatThrownBy(() -> persistence.save(classificationWith("gmail-id-duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Everything saved is waiting for the spreadsheet until something says otherwise —
     * which is what makes a failed sync recoverable rather than a lost day.
     */
    @Test
    void reportsEverythingAsWaitingForTheSpreadsheetUntilItIsMarked() {
        persistence.save(classificationWith("gmail-id-waiting-1"));
        persistence.save(classificationWith("gmail-id-waiting-2"));

        assertThat(persistence.findNotSyncedToSpreadsheet())
                .extracting(EmailClassification::gmailMessageId)
                .contains("gmail-id-waiting-1", "gmail-id-waiting-2");
    }

    @Test
    void stopsReportingWhatHasReachedTheSpreadsheet() {
        persistence.save(classificationWith("gmail-id-synced"));
        persistence.save(classificationWith("gmail-id-not-synced"));

        persistence.markSyncedToSpreadsheet(
                List.of("gmail-id-synced"), Instant.parse("2026-09-03T10:00:00Z"));

        assertThat(persistence.findNotSyncedToSpreadsheet())
                .extracting(EmailClassification::gmailMessageId)
                .doesNotContain("gmail-id-synced")
                .contains("gmail-id-not-synced");

        assertThat(rowFor("gmail-id-synced").get("sheet_synced_at")).isNotNull();
    }

    @Test
    void returnsTheOldestFirstSoTheSheetReadsInOrder() {
        persistence.save(classificationAt("gmail-id-newer", RECEIVED_AT.plusSeconds(60)));
        persistence.save(classificationAt("gmail-id-older", RECEIVED_AT));

        assertThat(persistence.findNotSyncedToSpreadsheet())
                .extracting(EmailClassification::gmailMessageId)
                .containsSubsequence("gmail-id-older", "gmail-id-newer");
    }

    @Test
    void marksNothingWhenGivenNothing() {
        assertThatCode(() -> persistence.markSyncedToSpreadsheet(
                List.of(), Instant.parse("2026-09-03T10:00:00Z")))
                .doesNotThrowAnyException();
    }

    private static EmailClassification classificationAt(String gmailMessageId, Instant receivedAt) {
        return new EmailClassification(
                gmailMessageId, receivedAt, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false);
    }

    private static EmailClassification classificationWith(String gmailMessageId) {
        return new EmailClassification(
                gmailMessageId, RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false);
    }

    private Map<String, Object> rowFor(String gmailMessageId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM email_classifications WHERE gmail_message_id = ?", gmailMessageId);
    }
}
