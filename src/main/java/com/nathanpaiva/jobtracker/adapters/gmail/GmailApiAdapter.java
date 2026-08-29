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
            // The mailbox being unreachable is not a per-email problem: nothing can be
            // processed, so the run should stop here rather than report an empty day.
            throw new UncheckedIOException("could not read the mailbox", e);
        }
    }

    /**
     * Gmail accepts the same search syntax as the web interface, and {@code after:}
     * takes a Unix timestamp in seconds. Spam and trash are excluded by default.
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
                    .setQ("after:" + since.getEpochSecond())
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
