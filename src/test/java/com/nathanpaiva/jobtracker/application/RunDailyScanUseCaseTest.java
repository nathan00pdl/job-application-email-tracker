package com.nathanpaiva.jobtracker.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.domain.UpdateType;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;
import com.nathanpaiva.jobtracker.ports.NotificationPort;
import com.nathanpaiva.jobtracker.ports.PersistencePort;
import com.nathanpaiva.jobtracker.ports.SpreadsheetPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole daily run, with the mailbox, the database, the spreadsheet and WhatsApp
 * replaced by lists.
 *
 * <p>No Spring, no container, no mocking framework, no credentials — and the run is
 * covered end to end. This is what the ports were for: the use case names only
 * interfaces, so a test can supply anything that satisfies them.
 *
 * <p>The classifier is <em>not</em> faked. It is domain code with no I/O, so the real
 * one runs here, and these tests check the pipeline against the actual rules rather than
 * against a stand-in that would need keeping in step with them.
 */
class RunDailyScanUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-31T06:00:00Z");

    private final InMemoryEmailSource mailbox = new InMemoryEmailSource();
    private final InMemoryPersistence database = new InMemoryPersistence();
    private final InMemorySpreadsheet sheet = new InMemorySpreadsheet();
    private final InMemoryNotification whatsapp = new InMemoryNotification();

    private final RunDailyScanUseCase useCase = new RunDailyScanUseCase(
            mailbox, new EmailClassifier(), database, sheet, whatsapp,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storesTheEmailsThatAreAboutAnApplication() {
        mailbox.contains(
                email("m1", "greenhouse.io", "Recebemos sua candidatura"),
                email("m2", "acme.com", "Sobre sua candidatura: convite para entrevista"));

        useCase.run();

        assertThat(database.saved)
                .extracting(EmailClassification::gmailMessageId)
                .containsExactly("m1", "m2");
        assertThat(database.saved)
                .extracting(EmailClassification::updateType)
                .containsExactly(UpdateType.APPLICATION_RECEIVED, UpdateType.INTERVIEW_INVITE);
    }

    /** An email that is not about an application leaves no trace at all. */
    @Test
    void storesNothingForUnrelatedEmails() {
        mailbox.contains(
                email("m1", "banco.example.com", "Sua fatura chegou"),
                email("m2", "newsletter.example.com", "5 vagas abertas esta semana"));

        useCase.run();

        assertThat(database.saved).isEmpty();
    }

    /**
     * The check that makes the job safe to run twice. Without it, a second run on the
     * same day would try to store everything again.
     */
    @Test
    void skipsEmailsItHasAlreadyStored() {
        database.alreadyHas("m1");
        mailbox.contains(
                email("m1", "greenhouse.io", "Recebemos sua candidatura"),
                email("m2", "gupy.io", "Sua candidatura: infelizmente não seguiremos"));

        useCase.run();

        assertThat(database.saved)
                .extracting(EmailClassification::gmailMessageId)
                .containsExactly("m2");
    }

    @Test
    void runningTwiceStoresNothingTheSecondTime() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));

        useCase.run();
        useCase.run();

        assertThat(database.saved).hasSize(1);
    }

    /**
     * The window is a little over a day on purpose: an exact 24 hours would drop
     * anything that arrived while a run was late or a previous one failed.
     */
    @Test
    void asksTheMailboxForALittleOverADayOnTheVeryFirstRun() {
        useCase.run();

        assertThat(mailbox.askedFor).isEqualTo(NOW.minus(Duration.ofHours(26)));
    }

    /**
     * Once a scan has finished, the window comes from the database rather than from
     * counting hours backwards. That is what makes a gap of any length close itself.
     */
    @Test
    void startsFromTheLastScanRatherThanAFixedNumberOfHours() {
        database.scanFinishedAt(NOW.minus(Duration.ofHours(3)));

        useCase.run();

        assertThat(mailbox.askedFor).isEqualTo(NOW.minus(Duration.ofHours(4)));
    }

    /**
     * The point of the whole change. A fixed window can only cover the gap it was sized
     * for; three days down used to mean two of them lost without a sound.
     */
    @Test
    void readsTheWholeGapWhenSeveralRunsWereMissed() {
        database.scanFinishedAt(NOW.minus(Duration.ofDays(9)));

        useCase.run();

        assertThat(mailbox.askedFor).isEqualTo(NOW.minus(Duration.ofDays(9)).minus(Duration.ofHours(1)));
    }

    /**
     * The mark is the instant the reading started, not the one it finished. A run takes
     * minutes, and an email arriving while it runs is not in the answer Gmail already
     * gave — recording the finish would place it before the next window and lose it.
     */
    @Test
    void recordsWhenTheReadingStarted() {
        useCase.run();

        assertThat(database.lastCompletedScan()).contains(NOW);
    }

    /**
     * Reading is finished once the emails are stored. Everything after it has a queue of
     * its own, so a spreadsheet outage must not make the mailbox be read again.
     */
    @Test
    void marksTheReadingDoneEvenWhenTheSpreadsheetFails() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));
        sheet.breaks();

        assertThatThrownBy(useCase::run).isInstanceOf(IllegalStateException.class);

        assertThat(database.lastCompletedScan()).contains(NOW);
    }

    @Test
    void doesNothingWhenThereIsNoNewMail() {
        useCase.run();

        assertThat(database.saved).isEmpty();
    }

    @Test
    void mirrorsWhatItStoredToTheSpreadsheet() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));

        useCase.run();

        assertThat(sheet.rows)
                .extracting(EmailClassification::gmailMessageId)
                .containsExactly("m1");
    }

    @Test
    void doesNotSendTheSameRowTwice() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));

        useCase.run();
        useCase.run();

        assertThat(sheet.rows).hasSize(1);
    }

    /**
     * A run whose spreadsheet call failed leaves rows behind. They are picked up on the
     * next run, so a day when Google is unreachable costs a delay and nothing else.
     */
    @Test
    void picksUpRowsLeftBehindByAFailedRun() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));
        sheet.breaks();

        assertThatThrownBy(useCase::run).isInstanceOf(IllegalStateException.class);
        assertThat(database.saved).hasSize(1);
        assertThat(sheet.rows).isEmpty();

        sheet.unreachable = false;
        useCase.run();

        assertThat(sheet.rows)
                .extracting(EmailClassification::gmailMessageId)
                .containsExactly("m1");
    }

    @Test
    void leavesTheSpreadsheetAloneWhenThereIsNothingWaiting() {
        useCase.run();

        assertThat(sheet.rows).isEmpty();
    }

    @Test
    void sendsWhatIsWaitingToBeReported() {
        mailbox.contains(
                email("m1", "greenhouse.io", "Recebemos sua candidatura"),
                email("m2", "gupy.io", "Infelizmente não seguiremos com sua candidatura"));

        useCase.run();

        assertThat(whatsapp.sent).hasSize(1);
        DailyDigest digest = whatsapp.sent.get(0);
        assertThat(digest.total()).isEqualTo(2);
        assertThat(digest.countOf(UpdateType.APPLICATION_RECEIVED)).isEqualTo(1);
        assertThat(digest.countOf(UpdateType.REJECTION)).isEqualTo(1);
        assertThat(digest.platforms()).containsExactly("Greenhouse", "Gupy");
    }

    /** Once delivered, an update leaves the queue: the next digest reports only what is new. */
    @Test
    void doesNotReportTheSameUpdateTwice() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));

        useCase.run();
        useCase.run();

        assertThat(whatsapp.sent).extracting(DailyDigest::total).containsExactly(1, 0);
        assertThat(database.digestQueue()).isEmpty();
    }

    /**
     * The reason the mark comes after the send. A refused digest leaves the queue as it
     * was, and the next run's digest carries what the failed one could not.
     */
    @Test
    void marksNothingWhenTheSendFailsAndSendsItOnTheNextRun() {
        mailbox.contains(email("m1", "greenhouse.io", "Recebemos sua candidatura"));
        whatsapp.refuses();

        assertThatThrownBy(useCase::run).isInstanceOf(IllegalStateException.class);
        assertThat(database.digestQueue())
                .extracting(EmailClassification::gmailMessageId)
                .containsExactly("m1");

        whatsapp.refusing = false;
        useCase.run();

        assertThat(whatsapp.sent).extracting(DailyDigest::total).containsExactly(1);
        assertThat(database.digestQueue()).isEmpty();
    }

    /** A day with no news is still reported, so that silence always means a failure. */
    @Test
    void stillSendsADayWithNothingInIt() {
        useCase.run();

        assertThat(whatsapp.sent).hasSize(1);
        assertThat(whatsapp.sent.get(0).isEmpty()).isTrue();
    }

    /**
     * The digest reports the period it found rather than one it was given — which is why
     * a delivery that fails costs a delay instead of a day of news.
     */
    @Test
    void readsThePeriodFromTheQueueRatherThanFromTheClock() {
        mailbox.contains(
                emailAt("m1", "greenhouse.io", "Recebemos sua candidatura", NOW.minus(Duration.ofHours(20))),
                emailAt("m2", "gupy.io", "Infelizmente não seguiremos", NOW.minus(Duration.ofHours(2))));

        useCase.run();

        DailyDigest digest = whatsapp.sent.get(0);
        assertThat(digest.earliest()).isEqualTo(NOW.minus(Duration.ofHours(20)));
        assertThat(digest.latest()).isEqualTo(NOW.minus(Duration.ofHours(2)));
    }

    private static IncomingEmail email(String id, String senderDomain, String subject) {
        return new IncomingEmail(id, NOW.minus(Duration.ofHours(2)), senderDomain,
                subject, "corpo do email");
    }

    /** Same, but with the arrival time spelled out, for the tests about the period. */
    private static IncomingEmail emailAt(String id, String senderDomain, String subject,
                                         Instant receivedAt) {
        return new IncomingEmail(id, receivedAt, senderDomain, subject, "corpo do email");
    }

    /** A mailbox that is a list, and remembers what it was asked for. */
    private static final class InMemoryEmailSource implements EmailSourcePort {

        private final List<IncomingEmail> emails = new ArrayList<>();
        private Instant askedFor;

        void contains(IncomingEmail... incoming) {
            emails.addAll(List.of(incoming));
        }

        @Override
        public List<IncomingEmail> fetchReceivedAfter(Instant since) {
            askedFor = since;
            return List.copyOf(emails);
        }
    }

    /** A spreadsheet that is a list, and can be told to fail. */
    private static final class InMemorySpreadsheet implements SpreadsheetPort {

        private final List<EmailClassification> rows = new ArrayList<>();
        private boolean unreachable;

        void breaks() {
            unreachable = true;
        }

        @Override
        public void append(List<EmailClassification> classifications) {
            if (unreachable) {
                throw new IllegalStateException("spreadsheet unreachable");
            }
            rows.addAll(classifications);
        }
    }

    /** A WhatsApp that is a list of the digests it accepted, and can be told to refuse. */
    private static final class InMemoryNotification implements NotificationPort {

        private final List<DailyDigest> sent = new ArrayList<>();
        private boolean refusing;

        void refuses() {
            refusing = true;
        }

        @Override
        public void send(DailyDigest digest) {
            if (refusing) {
                throw new IllegalStateException("WhatsApp refused the digest");
            }
            sent.add(digest);
        }
    }

    /** A database that is a list. */
    private static final class InMemoryPersistence implements PersistencePort {

        private final List<EmailClassification> saved = new ArrayList<>();
        private final List<String> knownIds = new ArrayList<>();
        private final List<String> syncedIds = new ArrayList<>();
        private final List<String> digestedIds = new ArrayList<>();
        private final List<Instant> completedScans = new ArrayList<>();

        List<EmailClassification> digestQueue() {
            return findNotSentInDigest();
        }

        void scanFinishedAt(Instant completedAt) {
            recordScanCompleted(completedAt);
        }

        void alreadyHas(String gmailMessageId) {
            knownIds.add(gmailMessageId);
        }

        @Override
        public void save(EmailClassification classification) {
            saved.add(classification);
            knownIds.add(classification.gmailMessageId());
        }

        @Override
        public boolean existsByGmailMessageId(String gmailMessageId) {
            return knownIds.contains(gmailMessageId);
        }

        @Override
        public List<EmailClassification> findNotSyncedToSpreadsheet() {
            return saved.stream()
                    .filter(classification -> !syncedIds.contains(classification.gmailMessageId()))
                    .toList();
        }

        @Override
        public void markSyncedToSpreadsheet(Collection<String> gmailMessageIds, Instant syncedAt) {
            syncedIds.addAll(gmailMessageIds);
        }

        @Override
        public List<EmailClassification> findNotSentInDigest() {
            return saved.stream()
                    .filter(classification -> !digestedIds.contains(classification.gmailMessageId()))
                    .toList();
        }

        @Override
        public void markSentInDigest(Collection<String> gmailMessageIds, Instant sentAt) {
            digestedIds.addAll(gmailMessageIds);
        }

        @Override
        public Optional<Instant> lastCompletedScan() {
            return completedScans.stream().max(Instant::compareTo);
        }

        @Override
        public void recordScanCompleted(Instant completedAt) {
            completedScans.add(completedAt);
        }
    }
}
