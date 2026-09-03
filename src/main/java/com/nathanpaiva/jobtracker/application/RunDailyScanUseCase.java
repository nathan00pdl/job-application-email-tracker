package com.nathanpaiva.jobtracker.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;
import com.nathanpaiva.jobtracker.ports.PersistencePort;
import com.nathanpaiva.jobtracker.ports.SpreadsheetPort;

/**
 * The daily run: read the mailbox, keep what is about a job application, store it.
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
     * How far back each run looks. Slightly more than a day on purpose: the job is
     * scheduled daily, and an exact 24 hours would drop anything that arrived while a
     * run was late or a previous one failed. Re-reading an email costs nothing, because
     * the ones already stored are skipped.
     */
    private static final Duration WINDOW = Duration.ofHours(26);

    private final EmailSourcePort emailSource;
    private final EmailClassifier classifier;
    private final PersistencePort persistence;
    private final SpreadsheetPort spreadsheet;
    private final Clock clock;

    public RunDailyScanUseCase(EmailSourcePort emailSource, EmailClassifier classifier,
                               PersistencePort persistence, SpreadsheetPort spreadsheet,
                               Clock clock) {
        this.emailSource = emailSource;
        this.classifier = classifier;
        this.persistence = persistence;
        this.spreadsheet = spreadsheet;
        this.clock = clock;
    }

    public void run() {
        readAndStoreNewEmails();
        mirrorToSpreadsheet();
    }

    private void readAndStoreNewEmails() {
        Instant since = clock.instant().minus(WINDOW);
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
}
