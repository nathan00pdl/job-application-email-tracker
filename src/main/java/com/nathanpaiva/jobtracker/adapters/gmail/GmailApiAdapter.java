package com.nathanpaiva.jobtracker.adapters.gmail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.api.client.http.HttpResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;
import com.nathanpaiva.jobtracker.ports.EmailSourcePort;

/**
 * Reads the mailbox through the Gmail API.
 *
 * <p>Two calls are needed per email: {@code messages.list} returns ids only, and the
 * content requires a {@code messages.get} for each one. That is a request per message,
 * which for the few dozen a day this job sees is not worth avoiding — but it is worth
 * knowing about before the volume changes.
 */
@Component
class GmailApiAdapter implements EmailSourcePort {

    private static final Logger log = LoggerFactory.getLogger(GmailApiAdapter.class);

    /** Gmail's alias for "the account that owns the credential". */
    private static final String AUTHENTICATED_USER = "me";

    /**
     * What Google answers when the refresh token is expired or revoked.
     *
     * <p>Worth naming because it is the failure this project meets most often: while the
     * OAuth app is in Testing, Google expires the refresh token every seven days.
     */
    private static final String EXPIRED_CREDENTIAL = "invalid_grant";

    private final Gmail gmail;

    GmailApiAdapter(Gmail gmail) {
        this.gmail = gmail;
    }

    @Override
    public List<IncomingEmail> fetchReceivedAfter(Instant since) {
        try {
            List<IncomingEmail> emails = new ArrayList<>();
            for (String messageId : messageIdsReceivedAfter(since)) {
                fetch(messageId).ifPresent(emails::add);
            }
            emails.sort(Comparator.comparing(IncomingEmail::receivedAt));
            log.info("read {} emails received after {}", emails.size(), since);
            return List.copyOf(emails);
        } catch (IOException e) {
            if (mentionsExpiredCredential(e)) {
                throw new IllegalStateException(
                        "Gmail refused the credential (invalid_grant). The refresh token has "
                                + "expired or was revoked: generate a new one and update "
                                + "GMAIL_REFRESH_TOKEN.", e);
            }
            // The mailbox being unreachable is not a per-email problem: nothing can be
            // processed, so the run should stop here rather than report an empty day.
            throw new UncheckedIOException("could not read the mailbox", e);
        }
    }

    /**
     * Whether this failure is Google saying the credential is no longer good.
     *
     * <p>Without this, an expired token and an unreachable network produce the same
     * sentence, and the one that happens every week is the one that says nothing. The
     * answer is three levels down: an {@code IOException} wrapping a
     * {@code GoogleAuthException} wrapping a 400 whose body carries the reason.
     *
     * <p>The body is what is inspected, not the status code. A 400 can mean several
     * things, and only one of them is fixed by generating a new token.
     *
     * <p>The walk is bounded rather than following causes until null, so a cycle in the
     * chain cannot hang the run.
     */
    private static boolean mentionsExpiredCredential(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof HttpResponseException response
                    && mentionsExpiredCredential(response.getContent())) {
                return true;
            }
            if (mentionsExpiredCredential(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsExpiredCredential(String text) {
        return text != null && text.contains(EXPIRED_CREDENTIAL);
    }

    /**
     * Gmail accepts the same search syntax as the web interface, and {@code after:}
     * takes a Unix timestamp in seconds. Spam and trash are excluded by default.
     *
     * <p>Sent mail is not, and is excluded here. A reply to a company usually quotes the
     * message it answers, so a reply to "recebemos sua candidatura" carries that phrase,
     * and a reply to an interview invitation carries the invitation. Read as incoming, each
     * would be stored as news the company sent — news that was really the author's own.
     *
     * <p>The loop is not optional. A page holds around a hundred ids, and without
     * following {@code nextPageToken} a busy day would silently lose whatever did not
     * fit on the first page — the kind of bug that only appears once it matters.
     */
    private List<String> messageIdsReceivedAfter(Instant since) throws IOException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        do {
            ListMessagesResponse response = gmail.users().messages()
                    .list(AUTHENTICATED_USER)
                    .setQ("after:" + since.getEpochSecond() + " -in:sent")
                    .setPageToken(pageToken)
                    .execute();

            if (response.getMessages() != null) {
                response.getMessages().forEach(message -> ids.add(message.getId()));
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null);

        return ids;
    }

    /**
     * One email that cannot be understood does not end the run.
     *
     * <p>The pipeline treats per-item failures as something to record and step over, so
     * a single malformed message never costs the whole day. The log keeps the id and the
     * reason — never the subject, the sender or the body.
     */
    private Optional<IncomingEmail> fetch(String messageId) throws IOException {
        Message message = gmail.users().messages()
                .get(AUTHENTICATED_USER, messageId)
                .setFormat("full")
                .execute();

        try {
            return Optional.of(GmailMessageMapper.toIncomingEmail(message));
        } catch (IllegalArgumentException e) {
            log.warn("skipping message {}: {}", messageId, e.getMessage());
            return Optional.empty();
        }
    }
}
