package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One Gmail message the daily scan has looked at, and how it was classified.
 *
 * <p>This is the centre of the domain. It knows nothing about Gmail, Claude, JPA or
 * Spring. That is what lets us build it and check it in a plain unit test, with no
 * container and no mocking framework.
 *
 * <p>It is a record, so it cannot change after it is created. It describes something
 * that already happened: an email arrived at a given time and was classified. No later
 * step should change that.
 *
 * <p>Its existence is the verdict. Emails that are not about a job application are not
 * turned into one of these and are never stored, so there is no field saying whether it
 * counts — every one of them does.
 *
 * <p>Four columns of {@code email_classifications} are left out on purpose.
 * {@code id}, {@code created_at} and {@code sheet_synced_at} are internal tracking for
 * storage and syncing, and {@code manual_status} is filled in by hand in the
 * spreadsheet. None of them are part of what a classification is.
 *
 * @param gmailMessageId        Gmail's id for the message; the key used to avoid
 *                              processing the same email twice
 * @param receivedAt            when the email arrived
 * @param senderDomain          the domain the email came from, such as {@code greenhouse.io}
 * @param platform              the hiring platform, when we can tell; may be null
 * @param company               the company hiring, when we can tell; may be null
 * @param roleTitle             the job title, when we can tell; may be null
 * @param updateType            the kind of news the email carries
 * @param summary               a short summary, kept in the email's original language; may be null
 * @param urgent                whether the email asks for something time-sensitive
 */
public record EmailClassification(
        String gmailMessageId,
        Instant receivedAt,
        String senderDomain,
        String platform,
        String company,
        String roleTitle,
        UpdateType updateType,
        String summary,
        boolean urgent
) {

    /**
     * Checks the required fields when the record is created. This way an
     * {@code EmailClassification} can never exist in a broken state, and other code
     * does not have to check for it.
     *
     * <p>Only four fields are required. The rest can be null on purpose: "we looked at
     * this email and took nothing from it" is a normal result, and building that should
     * not force us to invent values.
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
