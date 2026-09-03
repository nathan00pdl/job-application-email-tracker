package com.nathanpaiva.jobtracker.ports;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.nathanpaiva.jobtracker.domain.EmailClassification;

/**
 * What the application needs from storage.
 *
 * <p>This interface is declared on the inside and implemented on the outside. It names
 * no database, no SQL and no framework, so the code that uses it does not know or care
 * that the answer comes from PostgreSQL. Swapping the store means writing another
 * adapter; nothing in the domain or the application changes.
 *
 * <p>It stays this small on purpose. Reading the rows that still have to be copied to
 * the spreadsheet is real, but it belongs to the step that builds the spreadsheet sync,
 * not here. A port that grows ahead of its callers becomes a list of guesses.
 */
public interface PersistencePort {

    /**
     * Saves a classification.
     *
     * <p>Callers are expected to check {@link #existsByGmailMessageId(String)} first.
     * The unique constraint on the column is still the real guard: it is what makes the
     * daily job safe to run again after a partial failure, and it holds even if two runs
     * overlap and both pass the check before either saves.
     */
    void save(EmailClassification classification);

    /**
     * Whether an email with this Gmail id was already processed.
     *
     * <p>This is the check that keeps the daily job from looking at the same email
     * twice.
     */
    boolean existsByGmailMessageId(String gmailMessageId);

    /**
     * Classifications that have not reached the spreadsheet yet, oldest first.
     *
     * <p>Storing and mirroring are separate steps on purpose. If the spreadsheet cannot
     * be reached, the classification is already safe in the database and this method
     * finds it again on the next run — rather than the day's work being lost because a
     * second service was down.
     */
    List<EmailClassification> findNotSyncedToSpreadsheet();

    /**
     * Records that these reached the spreadsheet, so the next run leaves them alone.
     *
     * <p>Called after the append succeeds, never before. Marking first and appending
     * second would lose rows on failure; this way a failure means they are tried again,
     * and the worst case is a duplicated row rather than a missing one.
     */
    void markSyncedToSpreadsheet(Collection<String> gmailMessageIds, Instant syncedAt);
}
