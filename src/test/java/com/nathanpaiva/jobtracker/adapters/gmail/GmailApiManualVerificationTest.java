package com.nathanpaiva.jobtracker.adapters.gmail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.nathanpaiva.jobtracker.domain.IncomingEmail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the real mailbox, to confirm the credentials actually work.
 *
 * <p>It runs only when {@code GMAIL_REFRESH_TOKEN} is set in the environment, so CI —
 * which has no such variable and must never hold one — skips it without any flag or
 * profile to remember. To run it locally:
 *
 * <pre>
 * set -a &amp;&amp; source .env &amp;&amp; set +a
 * mvn test -Dtest=GmailApiManualVerificationTest
 * </pre>
 *
 * <p>It prints metadata only: how many emails were read, and their domain and arrival
 * time. No subject and no body, because a terminal buffer is not a place for the
 * contents of a mailbox.
 */
class GmailApiManualVerificationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "GMAIL_REFRESH_TOKEN", matches = ".+")
    void readsTheLastWeekFromTheRealMailbox() throws Exception {
        GmailProperties properties = new GmailProperties(
                System.getenv("GMAIL_CLIENT_ID"),
                System.getenv("GMAIL_CLIENT_SECRET"),
                System.getenv("GMAIL_REFRESH_TOKEN"));

        GmailApiAdapter adapter =
                new GmailApiAdapter(new GmailClientConfiguration().gmail(properties));

        List<IncomingEmail> emails =
                adapter.fetchReceivedAfter(Instant.now().minus(Duration.ofDays(7)));

        System.out.println("emails read in the last 7 days: " + emails.size());
        emails.forEach(email -> System.out.printf(
                "  %s  %s%n", email.receivedAt(), email.senderDomain()));

        assertThat(emails).isNotNull();
    }
}
