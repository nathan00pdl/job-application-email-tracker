package com.nathanpaiva.jobtracker.ports;

import com.nathanpaiva.jobtracker.domain.DailyDigest;

/**
 * Where the daily digest goes for a person to read.
 *
 * <p>The digest is handed over as data, not as a message. Turning it into words belongs
 * to whatever implements this, because the words depend on the channel: a WhatsApp
 * template has blanks that Meta approved in advance, an email would have a subject line,
 * and neither shape should reach the code that decides what the digest contains.
 *
 * <p><b>Returning means it was accepted.</b> An implementation returns only once the
 * channel has accepted the message, and throws otherwise. The caller relies on that: it
 * marks the classifications as sent only after this returns, so a delivery that fails
 * leaves them waiting, and the next run sends them again instead of losing them.
 *
 * <p>Accepted is as far as a sender can know. WhatsApp reports that a message reached
 * the phone later, through a webhook — a server listening for the receipt — and this
 * project runs no server.
 */
public interface NotificationPort {

    /**
     * Delivers the digest, including one with nothing in it. A day with no news is still
     * reported, so that silence always means something went wrong.
     */
    void send(DailyDigest digest);
}
