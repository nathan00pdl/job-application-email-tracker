package com.nathanpaiva.jobtracker.adapters.gmail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.EmailClassifier;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the classifier over the real mailbox and reports what it decided.
 *
 * <p>This is not an assertion about correctness — only a person can say whether a verdict
 * is right. It exists because the rules were tightened in #26 to kill four false
 * positives, and tightening rules is exactly how true positives start being missed. The
 * numbers alone cannot tell the difference, so this prints both sides for a human to read.
 *
 * <p>Runs only when {@code GMAIL_REFRESH_TOKEN} is set, so CI skips it:
 *
 * <pre>
 * set -a &amp;&amp; source .env &amp;&amp; set +a
 * ./mvnw test -Dtest=ClassifierAgainstRealMailboxTest
 * </pre>
 *
 * <p>Nothing is written to disk unless {@code -Dreport.dir=<path>} says where. Without
 * it the run prints a summary and the emails it kept, and leaves no trace. That way
 * keeping subjects and sender domains on disk is a choice made per run, rather than a
 * side effect of running the check — and when a file is asked for, it lands outside this
 * repository, so mailbox content never sits next to code that gets committed.
 */
class ClassifierAgainstRealMailboxTest {

    /**
     * How far back to look. Seven days by default, so the usual run is quick; pass
     * {@code -Dmailbox.days=90} to reach back far enough to find real application
     * emails, at the cost of one API call per message.
     */
    private static final Duration WINDOW =
            Duration.ofDays(Long.getLong("mailbox.days", 7));

    @Test
    @EnabledIfEnvironmentVariable(named = "GMAIL_REFRESH_TOKEN", matches = ".+")
    void showsWhatTheClassifierMakesOfTheRealMailbox() throws Exception {
        GmailProperties properties = new GmailProperties(
                System.getenv("GMAIL_CLIENT_ID"),
                System.getenv("GMAIL_CLIENT_SECRET"),
                System.getenv("GMAIL_REFRESH_TOKEN"));

        GmailApiAdapter adapter =
                new GmailApiAdapter(new GmailClientConfiguration().gmail(properties));
        EmailClassifier classifier = new EmailClassifier();

        List<IncomingEmail> emails = adapter.fetchReceivedAfter(Instant.now().minus(WINDOW));

        StringBuilder accepted = new StringBuilder();
        StringBuilder rejected = new StringBuilder();
        int kept = 0;

        for (IncomingEmail email : emails) {
            Optional<EmailClassification> verdict = classifier.classify(email);
            if (verdict.isPresent()) {
                EmailClassification c = verdict.get();
                kept++;
                accepted.append(String.format("  %-22s %-12s %-3s %-24s %s%n",
                        c.updateType(),
                        c.platform() == null ? "-" : c.platform(),
                        c.urgent() ? "URG" : "",
                        c.senderDomain(),
                        c.subject() == null ? "(no subject)" : c.subject()));
            } else {
                rejected.append(String.format("  %-24s %s%n",
                        email.senderDomain(), email.subject()));
            }
        }

        String report = String.format(
                "classifier check — %s%n"
                        + "read %d emails from the last %d days%n"
                        + "  kept    %d%n"
                        + "  ignored %d%n%n"
                        + "=== KEPT — check for anything that should not be here ===%n%s%n"
                        + "=== IGNORED — check for anything that SHOULD have been kept ===%n%s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")),
                emails.size(), WINDOW.toDays(), kept, emails.size() - kept,
                accepted, rejected);

        System.out.printf("read %d emails from the last %d days: kept %d, ignored %d%n",
                emails.size(), WINDOW.toDays(), kept, emails.size() - kept);
        System.out.println("=== KEPT ===");
        System.out.print(accepted.isEmpty() ? "  (none)\n" : accepted.toString());
        writeIfAsked(report);

        assertThat(emails).isNotNull();
    }

    /**
     * Saves the full report only when a directory was named, and says so either way.
     *
     * <p>Two runs of this check can be compared with {@code diff}, which is worth a file.
     * What is not worth it is a directory quietly filling with months of email subjects
     * that nobody remembers leaving there.
     */
    private static void writeIfAsked(String report) throws IOException {
        String directory = System.getProperty("report.dir");
        if (directory == null || directory.isBlank()) {
            System.out.println(
                    "full report not written — pass -Dreport.dir=<path> to keep one");
            return;
        }

        Path file = Path.of(directory, "classifier-check-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmm")) + ".txt");
        Files.writeString(file, report);
        System.out.println("full report written to " + file);
    }
}
