package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * An email the daily scan fetched, before anything has been decided about it.
 *
 * <p>This is the raw material the pipeline works on: what comes out of the mailbox and
 * goes into the rule filter and then the classifier. It carries no verdict of any kind
 * — that is what {@link EmailClassification} is for.
 *
 * <p>Like the rest of the domain, it knows nothing about Gmail. The Gmail adapter will
 * build these from its own API objects, so nothing further in the pipeline has to deal
 * with Google's types, and a different mailbox would only mean a different adapter.
 *
 * <p>{@code subject} and {@code body} are kept in their original language, mostly
 * pt-BR, as the project's language rule requires: everything is written in English
 * except data captured from emails.
 *
 * @param gmailMessageId Gmail's id for the message; the key used to avoid processing
 *                       the same email twice
 * @param receivedAt     when the email arrived
 * @param senderDomain   the domain the email came from, such as {@code greenhouse.io}
 * @param subject        the subject line, as written
 * @param body           the message text, as written
 */
public record IncomingEmail(
        String gmailMessageId,
        Instant receivedAt,
        String senderDomain,
        String subject,
        String body
) {

    /**
     * Checks the fields the pipeline cannot work without.
     *
     * <p>{@code subject} and {@code body} are only required to be non-null, not
     * non-blank: an email with an empty subject is unusual but real, and refusing to
     * process one would lose a message rather than protect anything.
     */
    public IncomingEmail {
        requireText(gmailMessageId, "gmailMessageId");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        requireText(senderDomain, "senderDomain");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
