package com.nathanpaiva.jobtracker.domain;

import java.util.Objects;

/**
 * What the classifier concluded about one email.
 *
 * <p>This is deliberately not an {@link EmailClassification}. That one needs
 * {@code matchedRuleFilter} as well, and the classifier has no idea whether the rule
 * filter matched — nor should it. Giving it the full record to fill would force it to
 * invent a value for a field that is not its business.
 *
 * <p>So the classifier reports only what it knows, and the use case joins the two
 * signals afterwards. Keeping them apart until then is what makes it possible to notice
 * when they disagree.
 *
 * @param relevant   whether this email is about a job application at all. Everything
 *                   below is only meaningful when this is true
 * @param updateType the kind of news the email carries
 * @param company    the company hiring, when the email says; may be null
 * @param roleTitle  the job title, when the email says; may be null
 * @param platform   the hiring platform, when the email says; may be null
 * @param summary    one short line, kept in the email's original language; may be null
 * @param urgent     whether the email asks for something time-sensitive
 */
public record ClassificationResult(
        boolean relevant,
        UpdateType updateType,
        String company,
        String roleTitle,
        String platform,
        String summary,
        boolean urgent
) {

    /**
     * Only the update type is required.
     *
     * <p>Everything else is optional because most emails do not carry it. A rejection
     * often names no role, and a platform notification often names no company. Demanding
     * those would mean either losing the email or inventing values, and both are worse
     * than a null.
     */
    public ClassificationResult {
        Objects.requireNonNull(updateType, "updateType must not be null");
    }
}
