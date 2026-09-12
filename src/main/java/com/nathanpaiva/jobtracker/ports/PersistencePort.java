package com.nathanpaiva.jobtracker.ports;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * Classifications that have not been delivered in a digest yet, oldest first.
     *
     * <p>A queue rather than a window over the last day. If a delivery fails, these rows
     * stay here and the next digest carries them, instead of a day of news disappearing
     * because one send failed. That matters because the digest is where these are
     * actually read: one that never arrives loses information, not just a notice.
     *
     * <p>The period a digest covers is therefore read from what this returns, never
     * configured — which is why {@code DailyDigest} has no clock and no window of its own.
     */
    List<EmailClassification> findNotSentInDigest();

    /**
     * Records that these were delivered in a digest, so the next one leaves them alone.
     *
     * <p>Called after the delivery succeeds, never before — the same order as the
     * spreadsheet sync, for the same reason. Marking first would drop from the queue
     * rows that never reached anyone.
     */
    void markSentInDigest(Collection<String> gmailMessageIds, Instant sentAt);

    /**
     * When a scan last finished reading the mailbox, if one ever did.
     *
     * <p>This is what tells the next run where to start looking. Asking the database
     * rather than counting back a fixed number of hours is what makes a gap of any length
     * close itself: a run that has been down for three days reads three days, and one
     * that ran an hour ago reads an hour.
     *
     * <p>Empty on a database that has never completed a scan, which the caller answers
     * with a starting window of its own.
     */
    Optional<Instant> lastCompletedScan();

    /**
     * Records that a scan finished reading the mailbox.
     *
     * <p>Called when the reading is done, not when the whole run is — the two are not
     * the same. Emails that have been read and stored are safe, and a later failure
     * somewhere else does not un-read them. Every other step has its own queue to
     * recover from, so tying this to the end of the run would make a spreadsheet outage
     * force the mailbox to be read again.
     */
    void recordScanCompleted(Instant completedAt);
}
