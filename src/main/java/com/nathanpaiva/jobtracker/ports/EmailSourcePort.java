package com.nathanpaiva.jobtracker.ports;

import java.time.Instant;
import java.util.List;

import com.nathanpaiva.jobtracker.domain.IncomingEmail;

/**
 * Where the application gets emails from.
 *
 * <p>The interface says nothing about Gmail. Reading the same mailbox over IMAP, or a
 * different mailbox entirely, would mean a different adapter and no change anywhere
 * else — the domain, the rule filter and the classifier never learn where an email
 * came from.
 */
public interface EmailSourcePort {

    /**
     * Emails that arrived after the given instant, oldest first.
     *
     * <p>The caller passes the instant rather than the adapter deciding on its own. An
     * adapter that called {@code Instant.now()} internally would be untestable without
     * freezing the clock, and the window would silently depend on when the job happened
     * to start. The use case owns that decision.
     */
    List<IncomingEmail> fetchReceivedAfter(Instant since);
}
