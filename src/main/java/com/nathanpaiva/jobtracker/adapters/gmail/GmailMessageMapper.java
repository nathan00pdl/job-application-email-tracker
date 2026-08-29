package com.nathanpaiva.jobtracker.adapters.gmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.nathanpaiva.jobtracker.domain.IncomingEmail;

/**
 * Turns a Gmail API message into an {@link IncomingEmail}.
 *
 * <p>This is where Google's shape stops. What the API returns is not an email as anyone
 * thinks of one: the subject and sender are entries in a list of header pairs, and the
 * text is somewhere inside a tree of MIME parts, base64url encoded. Everything past this
 * class works with a flat record instead.
 *
 * <p>It is a pure function — no network, no clock, no state — which is the whole reason
 * it is separate from the adapter. All the awkward cases live here and can be tested by
 * building a {@code Message} by hand, with no credentials and no calls to Google.
 */
final class GmailMessageMapper {

    private GmailMessageMapper() {
    }

    static IncomingEmail toIncomingEmail(Message message) {
        MessagePart payload = message.getPayload();
        if (payload == null) {
            throw new IllegalArgumentException(
                    "message " + message.getId() + " has no payload; it was probably fetched "
                            + "with a format that omits the body");
        }
        if (message.getInternalDate() == null) {
            throw new IllegalArgumentException("message " + message.getId() + " has no internalDate");
        }

        String from = headerValue(payload, "From").orElseThrow(() -> new IllegalArgumentException(
                "message " + message.getId() + " has no From header"));

        return new IncomingEmail(
                message.getId(),
                Instant.ofEpochMilli(message.getInternalDate()),
                senderDomainOf(from),
                headerValue(payload, "Subject").orElse(""),
                bodyTextOf(payload));
    }

    /** Header names are case-insensitive per RFC 5322, and Gmail does not normalise them. */
    private static Optional<String> headerValue(MessagePart payload, String name) {
        if (payload.getHeaders() == null) {
            return Optional.empty();
        }
        return payload.getHeaders().stream()
                .filter(header -> name.equalsIgnoreCase(header.getName()))
                .map(header -> header.getValue())
                .findFirst();
    }

    /**
     * Pulls the domain out of a From header.
     *
     * <p>The header comes in shapes like {@code Acme Careers <no-reply@greenhouse.io>} or
     * a bare {@code no-reply@greenhouse.io}. Taking everything after the last {@code @}
     * and stopping at the first character that cannot appear in a domain handles both
     * without parsing the whole address grammar, which is far larger than it looks.
     */
    private static String senderDomainOf(String fromHeader) {
        int at = fromHeader.lastIndexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("From header has no address: " + fromHeader);
        }

        String rest = fromHeader.substring(at + 1);
        int end = 0;
        while (end < rest.length() && isDomainCharacter(rest.charAt(end))) {
            end++;
        }

        String domain = rest.substring(0, end).toLowerCase(java.util.Locale.ROOT);
        if (domain.isBlank()) {
            throw new IllegalArgumentException("From header has no domain: " + fromHeader);
        }
        return domain;
    }

    private static boolean isDomainCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '-';
    }

    /**
     * Finds the readable text of the message.
     *
     * <p>A plain text part is preferred wherever it sits in the tree. Only when there is
     * none does the HTML part get used, with its tags stripped: sending raw HTML to the
     * classifier would spend tokens on markup and teach it nothing.
     */
    private static String bodyTextOf(MessagePart payload) {
        return firstPartOfType(payload, "text/plain")
                .map(GmailMessageMapper::decode)
                .or(() -> firstPartOfType(payload, "text/html")
                        .map(part -> stripHtml(decode(part))))
                .orElse("");
    }

    /** Walks the whole MIME tree: a message can nest multipart inside multipart. */
    private static Optional<MessagePart> firstPartOfType(MessagePart part, String mimeType) {
        if (mimeType.equalsIgnoreCase(baseMimeTypeOf(part)) && hasData(part)) {
            return Optional.of(part);
        }
        List<MessagePart> children = part.getParts();
        if (children != null) {
            for (MessagePart child : children) {
                Optional<MessagePart> found = firstPartOfType(child, mimeType);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /** {@code text/plain; charset=UTF-8} and {@code text/plain} are the same type here. */
    private static String baseMimeTypeOf(MessagePart part) {
        String mimeType = part.getMimeType();
        if (mimeType == null) {
            return "";
        }
        int parameters = mimeType.indexOf(';');
        return (parameters < 0 ? mimeType : mimeType.substring(0, parameters)).trim();
    }

    private static boolean hasData(MessagePart part) {
        return part.getBody() != null && part.getBody().getData() != null;
    }

    /**
     * Gmail encodes part bodies with the URL-safe base64 alphabet, using {@code -} and
     * {@code _} where standard base64 uses {@code +} and {@code /}. Decoding with the
     * standard decoder does not fail loudly — it corrupts any text that happens to use
     * those characters, which in practice means accented words.
     */
    private static String decode(MessagePart part) {
        byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * A deliberately blunt HTML to text conversion: drop script and style blocks, drop
     * every tag, undo the few entities that actually show up, and collapse whitespace.
     *
     * <p>It is not a parser and does not try to be one. The output is only ever read by
     * the classifier, which needs the words and not the structure, so the cost of being
     * wrong here is low.
     */
    private static String stripHtml(String html) {
        String withoutScripts = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        String withoutTags = withoutScripts.replaceAll("(?s)<[^>]+>", " ");
        String unescaped = withoutTags
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return unescaped.replaceAll("\\s+", " ").trim();
    }
}
