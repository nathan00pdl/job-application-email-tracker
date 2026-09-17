package com.nathanpaiva.jobtracker.adapters.gmail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * A character written as a code: {@code &atilde;}, {@code &#227;} or {@code &#xE3;}.
     * The lengths are bounded so a stray ampersand in ordinary text is never read as the
     * start of one.
     */
    private static final Pattern ENTITY =
            Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");

    /**
     * The named codes these emails actually use: markup's own five, every accented letter
     * Portuguese writes, and the punctuation that templates reach for. It is not HTML's
     * full list of more than two thousand, and does not need to be — a name missing here
     * is left as written, and a new one is a line to add when a real email shows it.
     */
    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("aacute", "á"), Map.entry("agrave", "à"), Map.entry("acirc", "â"),
            Map.entry("atilde", "ã"), Map.entry("eacute", "é"), Map.entry("ecirc", "ê"),
            Map.entry("iacute", "í"), Map.entry("oacute", "ó"), Map.entry("ocirc", "ô"),
            Map.entry("otilde", "õ"), Map.entry("uacute", "ú"), Map.entry("uuml", "ü"),
            Map.entry("ccedil", "ç"),
            Map.entry("Aacute", "Á"), Map.entry("Agrave", "À"), Map.entry("Acirc", "Â"),
            Map.entry("Atilde", "Ã"), Map.entry("Eacute", "É"), Map.entry("Ecirc", "Ê"),
            Map.entry("Iacute", "Í"), Map.entry("Oacute", "Ó"), Map.entry("Ocirc", "Ô"),
            Map.entry("Otilde", "Õ"), Map.entry("Uacute", "Ú"), Map.entry("Uuml", "Ü"),
            Map.entry("Ccedil", "Ç"),
            Map.entry("ordm", "º"), Map.entry("ordf", "ª"), Map.entry("deg", "°"),
            Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("hellip", "…"),
            Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("ldquo", "“"),
            Map.entry("rdquo", "”"), Map.entry("laquo", "«"), Map.entry("raquo", "»"),
            Map.entry("bull", "•"), Map.entry("middot", "·"), Map.entry("copy", "©"),
            Map.entry("reg", "®"), Map.entry("trade", "™"), Map.entry("euro", "€"));

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
            // The header value is not repeated here: this message reaches the log, and the
            // sender address is personal data that logs have no business keeping.
            throw new IllegalArgumentException("From header has no address");
        }

        String rest = fromHeader.substring(at + 1);
        int end = 0;
        while (end < rest.length() && isDomainCharacter(rest.charAt(end))) {
            end++;
        }

        String domain = rest.substring(0, end).toLowerCase(java.util.Locale.ROOT);
        if (domain.isBlank()) {
            throw new IllegalArgumentException("From header has no domain");
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
     * none does the HTML part get used, with its tags stripped: the classifier looks for
     * sentences, and raw markup breaks them apart with tags and attributes.
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
     * {@code _} where standard base64 uses {@code +} and {@code /}.
     *
     * <p>Getting this wrong fails in two different ways, and neither is the one people
     * expect. {@code Base64.getDecoder()} throws {@code IllegalArgumentException:
     * Illegal base64 character 5f} — loud, and easy to find. {@code getMimeDecoder()} is
     * the dangerous one: it silently drops every character outside its alphabet, so the
     * body comes back truncated or empty with no error at all.
     */
    private static String decode(MessagePart part) {
        byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    /**
     * A deliberately blunt HTML to text conversion: drop script and style blocks, drop
     * every tag, turn character codes back into characters, and collapse whitespace.
     *
     * <p>It is not a parser and does not try to be one. The classifier needs the words and
     * not the structure — but it needs the words exactly, because it matches phrases.
     * An email sent only as HTML often writes every accent as a code, and "n&amp;atilde;o
     * seguiremos" is not "não seguiremos" to a comparison of strings: a rejection from a
     * real mailbox was read as a confirmation for that reason alone.
     */
    private static String stripHtml(String html) {
        String withoutScripts = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ");
        String withoutTags = withoutScripts.replaceAll("(?s)<[^>]+>", " ");
        return decodeEntities(withoutTags)
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * One pass over the text, so every code is decoded exactly once: {@code &amp;atilde;}
     * becomes the literal text {@code &atilde;}, as its author wrote, and not {@code ã}.
     * Replacing the codes one name at a time, with {@code &amp;} anywhere but last, would
     * decode that twice.
     *
     * <p>The JDK has no HTML decoder. Spring's lives in its web module, which this
     * application does not use, and a library for this would be a new dependency to cover
     * a table and a regular expression.
     */
    private static String decodeEntities(String text) {
        return ENTITY.matcher(text).replaceAll(match -> {
            String code = match.group(1);
            String decoded = code.charAt(0) == '#' ? fromCodePoint(code) : NAMED_ENTITIES.get(code);
            return Matcher.quoteReplacement(decoded != null ? decoded : match.group());
        });
    }

    /** {@code #227} or {@code #xE3}; null when the number names no character. */
    private static String fromCodePoint(String code) {
        boolean hex = code.charAt(1) == 'x' || code.charAt(1) == 'X';
        int codePoint = Integer.parseInt(code.substring(hex ? 2 : 1), hex ? 16 : 10);
        return Character.isValidCodePoint(codePoint) ? Character.toString(codePoint) : null;
    }
}
