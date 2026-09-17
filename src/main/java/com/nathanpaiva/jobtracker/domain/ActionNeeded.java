package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One email that asks the reader to do something, as the digest lists it.
 *
 * <p>It carries what is needed to find the email and decide whether to open it now — the
 * kind of news, who sent it, when it arrived, whether it is urgent — and the Gmail id, so
 * whoever delivers the digest can point straight at the message. The id means nothing
 * without access to the mailbox, which is why the project already treats it as safe to
 * log.
 *
 * <p>It carries no text from the email. Not the subject, and not the body: the digest is
 * meant to say where to look, not to repeat what was said.
 *
 * @param gmailMessageId Gmail's id for the message
 * @param updateType     the kind of news
 * @param source         the hiring platform when it could be told, otherwise the sender's
 *                       domain — the most specific fact available about who wrote
 * @param receivedAt     when the email arrived
 * @param urgent         whether the email asks for something time-sensitive
 */
public record ActionNeeded(
        String gmailMessageId,
        UpdateType updateType,
        String source,
        Instant receivedAt,
        boolean urgent
) {

    public ActionNeeded {
        requireText(gmailMessageId, "gmailMessageId");
        Objects.requireNonNull(updateType, "updateType must not be null");
        requireText(source, "source");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }

    /**
     * The action a classification calls for, or nothing if it calls for none: it has to be
     * a kind that asks for action, or be urgent whatever its kind.
     */
    static Optional<ActionNeeded> of(EmailClassification classification) {
        Objects.requireNonNull(classification, "classification must not be null");
        if (!classification.updateType().asksForAction() && !classification.urgent()) {
            return Optional.empty();
        }
        String platform = classification.platform();
        String source = platform != null && !platform.isBlank()
                ? platform
                : classification.senderDomain();
        return Optional.of(new ActionNeeded(
                classification.gmailMessageId(), classification.updateType(), source,
                classification.receivedAt(), classification.urgent()));
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
