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
 * in advance. Its text is fixed; only the blanks change from one day to the next. This is
 * the approved text, and this class fills its fifteen blanks, in order:
 *
 * <pre>
 * Resumo diário das suas candidaturas, referente a {{1}}.
 *
 * Este resumo reúne os e-mails sobre processos seletivos que chegaram desde o envio
 * anterior, já classificados de forma automática.
 *
 * Chegaram {{2}} e-mails sobre candidaturas. Divisão por tipo de retorno: {{3}}.
 *
 * Pedem sua atenção, do mais importante para o menos importante. Cada item mostra o tipo
 * de retorno, a plataforma ou o domínio de quem enviou, a data de chegada e o link que
 * abre o e-mail direto no Gmail:
 * 1) {{4}}
 * …
 * 10) {{13}}
 *
 * Além desses, também pedem atenção: {{14}}.
 *
 * Propostas, testes técnicos, entrevistas, pedidos de informação e e-mails com prazo entram
 * na lista. Confirmações de inscrição, recusas e e-mails sem categoria não entram, mas
 * aparecem na contagem acima e ficam registrados na planilha de acompanhamento: {{15}}
 *
 * Vagas sem item aparecem com um traço. Mensagem automática, enviada uma vez por dia.
 * </pre>
 *
 * <p>One template for every day, so its shape never changes: a quiet day has a dash in each
 * of the ten places, and a busy one says how many more there are. The fixed text cannot
 * lose a line, which is why the places are always there.
 *
 * <p>The template and this class are tied: a change on Meta's side means a change here,
 * and the other way round. What has to match exactly is the name, the language and the
 * number of blanks.
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
     * The template's name and language, as registered with Meta. Both are part of what was
     * approved: another name or another language is another template.
     */
    static final String TEMPLATE = "resumo_diario_acoes";
    static final String LANGUAGE = "pt_BR";

    /** How many emails the template can name; the rest are counted in blank fourteen. */
    static final int PLACES = 10;

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
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(spreadsheetId, "spreadsheetId must not be null");

        List<String> values = new ArrayList<>();
        values.add(period(digest, now));
        values.add(String.valueOf(digest.total()));
        values.add(byKind(digest));

        List<ActionNeeded> actions = digest.actions();
        for (int place = 0; place < PLACES; place++) {
            values.add(place < actions.size() ? item(actions.get(place)) : EMPTY_PLACE);
        }

        int beyond = actions.size() - PLACES;
        values.add(beyond > 0 ? "mais " + beyond + ", veja a planilha" : NOTHING);
        values.add(SPREADSHEET.formatted(spreadsheetId));

        return new Template(TEMPLATE, values.stream().map(DigestMessage::oneLine).toList());
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
