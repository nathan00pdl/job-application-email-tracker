package com.nathanpaiva.jobtracker.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;
import com.nathanpaiva.jobtracker.ports.NotificationPort;
import com.nathanpaiva.jobtracker.ports.PersistencePort;
import com.nathanpaiva.jobtracker.ports.SpreadsheetPort;

/**
 * The daily run: read the mailbox, keep what is about a job application, store it, and
 * report it.
 *
 * <p>This class holds the order of the steps and nothing else. Every question about what
 * an email <em>means</em> belongs to {@link EmailClassifier}, and every question about
 * how emails are read or stored belongs behind a port. If a decision here ever depends
 * on the content of an email, it is in the wrong place.
 *
 * <p>It knows no vendor either: it works the same whether emails come from the Gmail API
 * or anywhere else, which is what lets the whole run be tested with a list in memory.
 */
public class RunDailyScanUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunDailyScanUseCase.class);

    /**
     * How far back the very first run looks, when there is no earlier scan to start from.
     *
     * <p>Slightly more than a day, which is what every run used before the database
     * started remembering: a schedule that runs daily, plus enough slack to survive one
     * late or failed attempt.
     */
    private static final Duration FIRST_RUN_WINDOW = Duration.ofHours(26);

    /**
     * How far before the last scan to start reading again.
     *
     * <p>Gmail can index a message a little after it arrives, so a window that began
     * exactly where the last one ended could step over something that landed on the
     * boundary. Overlapping costs nothing — anything already stored is skipped by
     * {@code existsByGmailMessageId} — and an hour is far more than the gap needs to be.
     */
    private static final Duration OVERLAP = Duration.ofHours(1);

    private final EmailSourcePort emailSource;
    private final EmailClassifier classifier;
    private final PersistencePort persistence;
    private final SpreadsheetPort spreadsheet;
    private final NotificationPort notification;
    private final Clock clock;

    /**
     * Takes ports, not adapters — and the difference between those two words is the
     * whole of the dependency rule.
     *
     * <p>Receiving collaborators as arguments is <em>injection</em>: a mechanism, and by
     * itself it proves nothing. Taking a {@code GmailApiAdapter} here would still be
     * injection, and would still be testable. What matters is <em>what</em> the arguments
     * are: interfaces declared in a package that belongs to the inside. That is what
     * keeps this class from naming a vendor, and it is why the arrow of dependency points
     * inward instead of out.
     */
    public RunDailyScanUseCase(EmailSourcePort emailSource, EmailClassifier classifier,
                               PersistencePort persistence, SpreadsheetPort spreadsheet,
                               NotificationPort notification, Clock clock) {
        this.emailSource = emailSource;
        this.classifier = classifier;
        this.persistence = persistence;
        this.spreadsheet = spreadsheet;
        this.notification = notification;
        this.clock = clock;
    }

    public void run() {
        readAndStoreNewEmails();
        mirrorToSpreadsheet();
        deliverTheDigest();
    }

    /**
     * Reads what has arrived since the last scan finished, and keeps what is about an
     * application.
     *
     * <p>The starting point comes from the database, not from counting hours backwards.
     * That is what makes a gap of any length close itself: down for three days, it reads
     * three days; run an hour ago, it reads an hour. A fixed window can only ever cover
     * the gap it was sized for, and anything longer is lost without a sound — the
     * duplicate check skips what was already seen, it never goes looking for what was
     * missed.
     *
     * <p>It matters here more than it would elsewhere, because the gaps are scheduled:
     * while the OAuth app is in Testing, Google expires the refresh token every seven
     * days, and between the expiry and the renewal there are runs that fail.
     *
     * <p>What is recorded is the instant the reading <em>started</em>, not the one it
     * finished. A run takes minutes, and an email that arrives while it is running is not
     * in the answer Gmail already gave. Recording the finish would place it before the
     * next window and lose it; recording the start means the next run reads it again.
     *
     * <p>The mark is written at the end of <em>this</em> step, not at the end of the
     * run. Emails already stored are safe, and the steps that follow have queues of
     * their own to recover from; tying the mark to the whole run would make a spreadsheet
     * outage force the mailbox to be read again.
     */
    private void readAndStoreNewEmails() {
        Instant startedAt = clock.instant();
        Instant since = persistence.lastCompletedScan()
                .map(lastScan -> lastScan.minus(OVERLAP))
                .orElseGet(() -> startedAt.minus(FIRST_RUN_WINDOW));

        List<IncomingEmail> emails = emailSource.fetchReceivedAfter(since);

        int stored = 0;
        int skipped = 0;
        int ignored = 0;

        for (IncomingEmail email : emails) {
            if (persistence.existsByGmailMessageId(email.gmailMessageId())) {
                skipped++;
                continue;
            }
            EmailClassification classification = classifier.classify(email).orElse(null);
            if (classification == null) {
                ignored++;
                continue;
            }
            persistence.save(classification);
            stored++;
        }

        persistence.recordScanCompleted(startedAt);

        log.info("read {} emails since {}: stored {}, already seen {}, not about an application {}",
                emails.size(), since, stored, skipped, ignored);
    }

    /**
     * Copies to the spreadsheet whatever has not reached it yet.
     *
     * <p>This is not limited to what was stored a moment ago. A run whose spreadsheet
     * call failed leaves rows behind, and they are picked up here on the next run — so a
     * day when Google is unreachable costs nothing beyond a delay.
     *
     * <p>Marking happens after the append returns. A failure therefore means the rows
     * are tried again, and the worst outcome is a duplicated row rather than one that
     * silently never arrives.
     *
     * <p>A failure here is not caught. The classifications are already safe in the
     * database, so nothing is lost — but a spreadsheet quietly falling behind is worse
     * than a job that goes red and says so.
     */
    private void mirrorToSpreadsheet() {
        List<EmailClassification> waiting = persistence.findNotSyncedToSpreadsheet();
        if (waiting.isEmpty()) {
            return;
        }

        spreadsheet.append(waiting);
        persistence.markSyncedToSpreadsheet(
                waiting.stream().map(EmailClassification::gmailMessageId).toList(),
                clock.instant());

        log.info("mirrored {} classifications to the spreadsheet", waiting.size());
    }

    /**
     * Sends everything still waiting to be delivered, and marks it once it has gone.
     *
     * <p><b>The mark comes after the send, and only then.</b> Marking means "this reached
     * the reader". A send that fails throws before the mark, so the classifications stay
     * in the queue and go out in the next run's digest, whose period stretches to cover
     * them. Marking first would empty the queue on a day when nothing arrived.
     *
     * <p>The digest goes out even when it is empty. A day with no news is still reported,
     * so that a morning without a message means something went wrong, not that nothing
     * happened.
     *
     * <p>Note what is not passed in: no window, and no instant. The digest covers what the
     * database says is undelivered, whether that is one day or three, and reports the
     * period it found. That is the difference between a queue and a window.
     *
     * <p>What is marked is the very list the digest was built from, not the answer to a
     * second query, so nothing can be marked as delivered without having been counted in
     * the message.
     */
    private void deliverTheDigest() {
        List<EmailClassification> waiting = persistence.findNotSentInDigest();
        DailyDigest digest = DailyDigest.of(waiting);

        if (digest.isEmpty()) {
            log.info("digest: nothing waiting to be reported");
        } else {
            log.info("digest: {} classifications from {} to {}, {} urgent, by type {}, platforms {}",
                    digest.total(), digest.earliest(), digest.latest(), digest.urgent(),
                    digest.countsByType(), digest.platforms());
        }

        notification.send(digest);

        if (!waiting.isEmpty()) {
            persistence.markSentInDigest(
                    waiting.stream().map(EmailClassification::gmailMessageId).toList(),
                    clock.instant());
        }
    }
}
