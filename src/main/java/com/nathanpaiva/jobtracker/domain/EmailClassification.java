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
 * <p>Four columns of {@code email_classifications} are left out on purpose.
 * {@code id}, {@code created_at} and {@code sheet_synced_at} are internal tracking for
 * storage and syncing, and {@code manual_status} is filled in by hand in the
 * spreadsheet. None of them are part of what a classification is.
 *
 * <p>Two more columns are missing for a different reason. {@code has_disagreement} and
 * {@code included_in_digest} are not given to this record: they are worked out from
 * {@code matchedRuleFilter} and {@code llmClassifiedRelevant} by the methods below.
 * Storing them as fields would allow an object whose flags contradict the values they
 * came from, and nothing would catch it.
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
 * @param matchedRuleFilter     whether the cheap rule filter flagged this email
 * @param llmClassifiedRelevant whether the LLM judged it to be about a job application
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
        boolean urgent,
        boolean matchedRuleFilter,
        boolean llmClassifiedRelevant
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

    /**
     * Whether this email counts for the day: it is included in the WhatsApp summary and
     * in the daily counts.
     *
     * <p>The LLM has the final say. The rule filter is only a cheap first pass that
     * decides which emails are worth sending to the LLM at all, so it cannot overrule
     * the answer it asked for.
     *
     * <p>This reads as a plain copy of one field today, and it is. It exists as a named
     * method so the rule has one home: if "counts for the day" ever needs more than one
     * signal, this is the only place that changes, and every caller follows.
     */
    public boolean includedInDigest() {
        return llmClassifiedRelevant;
    }

    /**
     * Whether the two classification signals reached different answers.
     *
     * <p>The common case is the rule filter matching an email that the LLM then judges
     * unrelated to a job application. Such an email is still saved, not thrown away, and
     * is highlighted in the spreadsheet for a human to look at.
     *
     * <p>The point is not to correct the day's numbers, which follow the LLM either way.
     * It is to leave a trail: if the rule filter and the LLM start disagreeing more
     * often, the filter has drifted away from the emails actually being received, and
     * the only way to notice is to have kept the two answers apart.
     */
    public boolean hasDisagreement() {
        return matchedRuleFilter != llmClassifiedRelevant;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
