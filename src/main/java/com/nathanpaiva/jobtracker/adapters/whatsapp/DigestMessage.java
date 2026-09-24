package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nathanpaiva.jobtracker.domain.ActionNeeded;
import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.UpdateType;

/**
 * Fills the blanks of the {@code resumo_diario_acoes} template from a {@link DailyDigest}.
 *
 * <p>A WhatsApp message that the business sends first has to be a template Meta approved
 * in advance. Its text is fixed; only the blanks change from one day to the next. The
 * approved text is {@link #TEXT}, and this class fills its ten blanks.
 *
 * <p>One template for every day, so its shape never changes: a quiet day has a dash in each
 * of the six places, and a busy one says how many more there are. The fixed text cannot
 * lose a line, which is why the places are always there.
 *
 * <p>The text is deliberately short. An earlier one explained the format in three
 * paragraphs, and those 874 characters left no room for a single email once the link to
 * the spreadsheet was in: Meta refused the message whole.
 *
 * <p><b>The filled message must fit in {@value #MAX_LENGTH} characters.</b> Meta counts the
 * fixed text and the values together, and refuses a longer message outright — code 132005,
 * "Translated text too long" — so a day with too much to list would deliver nothing at
 * all. This class measures the message it is about to send, and lists only the emails that
 * fit; the rest are counted in "além desses". A shorter list is a message; a longer one
 * is none.
 *
 * <p>The template and this class are tied: a change on Meta's side means a change here,
 * and the other way round. What has to match exactly is the name, the language, the number
 * of blanks — and, for the measuring to be right, the text itself.
 *
 * <p>The words are Portuguese because a person in Brazil reads them, inside a message
 * whose fixed text is Portuguese too. Like the phrases the classifier looks for, they are
 * data the reader sees, not part of the code's own vocabulary.
 *
 * <p>A pure function: no network, no clock, no state. The moment of sending and the
 * spreadsheet are passed in rather than read, so every case can be checked by hand.
 */
final class DigestMessage {

    /**
     * The template's name and language, as registered with Meta. Meta finds a template by
     * the two together, so another name or another language is another template — and one
     * that does not exist is refused with code 132001, "template name does not exist in the
     * translation".
     *
     * <p>The language is {@code en} because that is how the template was registered, not
     * because of what it says: the text below is Portuguese, and Meta never translates
     * anything. The tag only picks which approved version to send.
     */
    static final String TEMPLATE = "resumo_diario_lista";
    static final String LANGUAGE = "en";

    /**
     * The template exactly as approved, blanks included. Kept here, and not only on Meta's
     * side, because it is what the length of the filled message is measured against.
     */
    static final String TEXT = """
            Resumo de {{1}}: {{2}}.

            Pedem sua atenção:
            1) {{3}}
            2) {{4}}
            3) {{5}}
            4) {{6}}
            5) {{7}}
            6) {{8}}

            Além desses: {{9}}. Os demais estão na planilha: {{10}}

            Mensagem automática diária.""";

    /** How many emails the template can name; the rest are counted in blank nine. */
    static final int PLACES = 6;

    /** The most characters Meta accepts in the body, fixed text and values together. */
    static final int MAX_LENGTH = 1024;

    /**
     * Opens one message in Gmail on the web, for the account signed in first. The id is
     * the one the API gives, which is the one stored.
     */
    private static final String GMAIL_MESSAGE = "https://mail.google.com/mail/u/0/#all/";

    private static final String SPREADSHEET = "https://docs.google.com/spreadsheets/d/%s/edit";

    /**
     * Which template to send, and the values of its blanks, in order.
     *
     * @param name   the template's name, as registered with Meta
     * @param values one value per blank
     */
    record Template(String name, List<String> values) {

        Template {
            Objects.requireNonNull(name, "name must not be null");
            values = List.copyOf(values);
        }
    }

    /**
     * The reader is in Brazil, so the dates are the reader's dates, in the same zone the
     * spreadsheet writes in. In UTC, an email that arrived at half past nine at night
     * would already belong to the next day.
     */
    private static final ZoneId READER_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    /** What goes in a blank that has nothing to hold, so the message never reads ": ." */
    private static final String NOTHING = "nenhum";

    /** What goes in a place with no email in it. */
    private static final String EMPTY_PLACE = "—";

    /**
     * How each kind of update is named, and the order the counts are listed in.
     *
     * <p>What asks something of the reader comes first — an offer, an interview, a test, a
     * request — then news that asks nothing, and last what fits no category.
     */
    private static final List<Kind> KINDS = List.of(
            new Kind(UpdateType.OFFER, "proposta", "propostas"),
            new Kind(UpdateType.INTERVIEW_INVITE, "entrevista", "entrevistas"),
            new Kind(UpdateType.TECHNICAL_TEST, "teste técnico", "testes técnicos"),
            new Kind(UpdateType.INFO_REQUEST, "pedido de informação", "pedidos de informação"),
            new Kind(UpdateType.REJECTION, "recusa", "recusas"),
            new Kind(UpdateType.APPLICATION_RECEIVED,
                    "confirmação de inscrição", "confirmações de inscrição"),
            new Kind(UpdateType.OTHER, "sem categoria", "sem categoria"));

    private static final Map<UpdateType, Kind> KIND_OF =
            KINDS.stream().collect(Collectors.toMap(Kind::type, Function.identity()));

    private DigestMessage() {
    }

    /**
     * The template, with its blanks filled.
     *
     * @param digest        what to report
     * @param now           the moment of sending; it only dates a digest with nothing in it
     * @param spreadsheetId the id of the spreadsheet the classifications are mirrored to
     */
    static Template forDigest(DailyDigest digest, Instant now, String spreadsheetId) {
        return forDigest(digest, now, spreadsheetId, MAX_LENGTH);
    }

    /**
     * The same, against a limit of the caller's choosing — so a test can check what the
     * list holds and in what order without the limit of today's text deciding it.
     *
     * <p>What gives way, in the order that loses least: first the listed emails, the least
     * important one at a time, each counted in "além desses" so nothing that waits on the
     * reader goes unmentioned; then, if the message still does not fit with none listed,
     * the counts by kind, leaving just how many arrived. The period, the total and the
     * link to the spreadsheet always stay.
     */
    static Template forDigest(DailyDigest digest, Instant now, String spreadsheetId, int maxLength) {
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(spreadsheetId, "spreadsheetId must not be null");

        int listed = Math.min(digest.actions().size(), PLACES);
        List<String> values = values(digest, now, spreadsheetId, listed, summary(digest));
        while (listed > 0 && lengthOf(values) > maxLength) {
            listed--;
            values = values(digest, now, spreadsheetId, listed, summary(digest));
        }
        if (lengthOf(values) > maxLength) {
            values = values(digest, now, spreadsheetId, 0, howMany(digest));
        }
        return new Template(TEMPLATE, values);
    }

    /** The ten values, with the first {@code listed} actions in their places. */
    private static List<String> values(DailyDigest digest, Instant now, String spreadsheetId,
                                       int listed, String summary) {
        List<String> values = new ArrayList<>();
        values.add(period(digest, now));
        values.add(summary);

        List<ActionNeeded> actions = digest.actions();
        for (int place = 0; place < PLACES; place++) {
            values.add(place < listed ? item(actions.get(place)) : EMPTY_PLACE);
        }

        // Just the number: the fixed text points at the spreadsheet in the next sentence.
        int beyond = actions.size() - listed;
        values.add(beyond > 0 ? "mais " + beyond : NOTHING);
        values.add(SPREADSHEET.formatted(spreadsheetId));

        return values.stream().map(DigestMessage::oneLine).toList();
    }

    /** How long the message is once these values fill {@link #TEXT}, as Meta counts it. */
    static int lengthOf(List<String> values) {
        String message = TEXT;
        for (int blank = values.size(); blank >= 1; blank--) {
            message = message.replace("{{" + blank + "}}", values.get(blank - 1));
        }
        return message.length();
    }

    /**
     * The days the digest covers, as the reader counts them.
     *
     * <p>Usually one day. When a delivery failed, the next digest carries everything still
     * waiting, and the period grows to cover it — "13/09 a 15/09" — so a backlog does not
     * pass for a busy day. A digest with nothing in it covers no period, so it is dated by
     * the day it is sent.
     */
    private static String period(DailyDigest digest, Instant now) {
        if (digest.isEmpty()) {
            return day(now);
        }
        LocalDate first = digest.earliest().atZone(READER_ZONE).toLocalDate();
        LocalDate last = digest.latest().atZone(READER_ZONE).toLocalDate();
        return first.equals(last)
                ? DAY.format(first)
                : DAY.format(first) + " a " + DAY.format(last);
    }

    /** "2 entrevistas, 10 confirmações de inscrição", naming only the kinds that occurred. */
    /** "14 e-mails: 2 entrevistas, 10 confirmações de inscrição". */
    private static String summary(DailyDigest digest) {
        String counted = byKind(digest);
        return counted.equals(NOTHING) ? howMany(digest) : howMany(digest) + ": " + counted;
    }

    /** Just how many arrived — what is left when the kinds do not fit beside them. */
    private static String howMany(DailyDigest digest) {
        return digest.total() == 1 ? "1 e-mail" : digest.total() + " e-mails";
    }

    private static String byKind(DailyDigest digest) {
        String counted = KINDS.stream()
                .filter(kind -> digest.countOf(kind.type()) > 0)
                .map(kind -> kind.counted(digest.countOf(kind.type())))
                .collect(Collectors.joining(", "));
        return counted.isEmpty() ? NOTHING : counted;
    }

    /**
     * "Entrevista · URGENTE · Gupy · 17/09 · https://mail.google.com/…" — enough to decide
     * whether to open it now, and one tap to do so.
     */
    private static String item(ActionNeeded action) {
        List<String> parts = new ArrayList<>();
        parts.add(capitalised(KIND_OF.get(action.updateType()).one()));
        if (action.urgent()) {
            parts.add("URGENTE");
        }
        parts.add(action.source());
        parts.add(day(action.receivedAt()));
        parts.add(GMAIL_MESSAGE + action.gmailMessageId());
        return String.join(" · ", parts);
    }

    private static String day(Instant instant) {
        return DAY.format(instant.atZone(READER_ZONE).toLocalDate());
    }

    private static String capitalised(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Meta refuses a template value that holds a line break, a tab or more than four
     * spaces in a row, and the whole message with it.
     *
     * <p>None of the values above should hold one: the platform names come from this
     * project's own list, and domains cannot contain spaces. But a refused digest is a day
     * of news that never arrives, so the rule is enforced here instead of trusted.
     */
    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    /** A kind of update, with its name in the singular and in the plural. */
    private record Kind(UpdateType type, String one, String many) {

        String counted(int count) {
            return count + " " + (count == 1 ? one : many);
        }
    }
}
