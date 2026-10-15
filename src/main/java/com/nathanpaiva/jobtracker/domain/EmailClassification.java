package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One Gmail message the daily scan has looked at, and how it was classified.
 *
 * <p>This is the centre of the domain. It knows nothing about Gmail, JPA or Spring. That
 * is what lets us build it and check it in a plain unit test, with no container and no
 * mocking framework.
 *
 * <p>It is a record, so it cannot change after it is created. It describes something
 * that already happened: an email arrived at a given time and was classified. No later
 * step should change that.
 *
 * <p>Its existence is the verdict. Emails that are not about a job application are not
 * turned into one of these and are never stored, so there is no field saying whether it
 * counts — every one of them does.
 *
 * <p>Four columns of {@code email_classifications} are left out on purpose: {@code id}
 * and {@code created_at} belong to the database, {@code sheet_synced_at} to the
 * spreadsheet sync, and {@code digest_sent_at} to the digest. All four are tracking about
 * the row, not part of what a classification is.
 *
 * @param gmailMessageId        Gmail's id for the message; the key used to avoid
 *                              processing the same email twice
 * @param receivedAt            when the email arrived
 * @param senderDomain          the domain the email came from, such as {@code greenhouse.io}
 * <p>It names no company and no role. The classifier does not read those out of an
 * email — a guess would look like a fact — and the subject, kept as written, already
 * names both for a person to read.
 *
 * @param platform              the hiring platform, when we can tell; may be null
 * @param updateType            the kind of news the email carries
 * @param subject               the email's subject line, as the sender wrote it, in its
 *                              original language; may be null
 * @param urgent                whether the email asks for something time-sensitive
 */
public record EmailClassification(
        String gmailMessageId,
        Instant receivedAt,
        String senderDomain,
        String platform,
        UpdateType updateType,
        String subject,
        boolean urgent
) {

    /**
     * Checks the required fields when the record is created. This way an
     * {@code EmailClassification} can never exist in a broken state, and other code
     * does not have to check for it.
     *
     * <p>Four fields are required. The platform and the subject can be null on purpose:
     * an email from a company's own address has no platform, and one can arrive with no
     * subject — building those should not force us to invent values.
     */
    public EmailClassification {
        requireText(gmailMessageId, "gmailMessageId");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        requireText(senderDomain, "senderDomain");
        Objects.requireNonNull(updateType, "updateType must not be null");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
