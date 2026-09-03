package com.nathanpaiva.jobtracker.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.domain.UpdateType;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;
import com.nathanpaiva.jobtracker.ports.PersistencePort;
import com.nathanpaiva.jobtracker.ports.SpreadsheetPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole daily run, with the mailbox and the database replaced by two lists.
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

    private final RunDailyScanUseCase useCase = new RunDailyScanUseCase(
            mailbox, new EmailClassifier(), database, sheet,
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
    void asksTheMailboxForALittleOverADay() {
        useCase.run();

        assertThat(mailbox.askedFor).isEqualTo(NOW.minus(Duration.ofHours(26)));
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

    private static IncomingEmail email(String id, String senderDomain, String subject) {
        return new IncomingEmail(id, NOW.minus(Duration.ofHours(2)), senderDomain,
                subject, "corpo do email");
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

    /** A database that is a list. */
    private static final class InMemoryPersistence implements PersistencePort {

        private final List<EmailClassification> saved = new ArrayList<>();
        private final List<String> knownIds = new ArrayList<>();
        private final List<String> syncedIds = new ArrayList<>();

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
    }
}
