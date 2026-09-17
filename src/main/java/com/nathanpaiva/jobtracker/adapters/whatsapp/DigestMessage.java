package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.UpdateType;

/**
 * Chooses the template for a {@link DailyDigest} and fills its blanks.
 *
 * <p>A WhatsApp message that the business sends first has to be a template Meta approved
 * in advance. Its text is fixed; only the blanks change from one day to the next. So a day
 * with news and a day without are two templates, not one: no value put in a blank can
 * remove a line of fixed text.
 *
 * <p>A day with news uses {@code resumo_diario}, with five blanks:
 *
 * <pre>
 * Resumo diário das suas candidaturas, referente a {{1}}.
 *
 * Novidades recebidas: {{2}}, das quais {{3}} pedem atenção urgente.
 *
 * Por tipo de atualização: {{4}}.
 *
 * Plataformas de origem: {{5}}.
 *
 * Os detalhes de cada e-mail estão na planilha de acompanhamento.
 * </pre>
 *
 * <p>A day without uses {@code resumo_diario_vazio}, with one. Sent through the first
 * template, it would end by pointing the reader at a spreadsheet with nothing new in it:
 *
 * <pre>
 * Resumo diário das suas candidaturas, referente a {{1}}.
 *
 * Nenhuma novidade.
 *
 * A planilha só é alterada quando houver novidades.
 * </pre>
 *
 * <p>The templates and this class are tied: a change on Meta's side means a change here,
 * and the other way round. What has to match exactly is each template's name, its
 * language and how many blanks it has.
 *
 * <p>The words are Portuguese because a person in Brazil reads them, inside a message
 * whose fixed text is Portuguese too. Like the phrases the classifier looks for, they are
 * data the reader sees, not part of the code's own vocabulary.
 *
 * <p>A pure function: no network, no clock, no state. The moment of sending is passed in
 * rather than read, so every case, midnight included, can be checked by hand.
 */
final class DigestMessage {

    /**
     * The templates' names and language, as registered with Meta. All are part of what was
     * approved: another name or another language is another template.
     */
    static final String TEMPLATE = "resumo_diario";
    static final String EMPTY_TEMPLATE = "resumo_diario_vazio";
    static final String LANGUAGE = "pt_BR";

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

    /**
     * What goes in the platforms blank when no platform could be told, so the message
     * never reads "Plataformas de origem: ."
     */
    private static final String NOTHING = "nenhuma";

    /**
     * How each kind of update is named, and the order they are listed in.
     *
     * <p>What asks something of the reader comes first — an offer, an interview, a test, a
     * request — then news that asks nothing, and last what fits no category. On a phone
     * screen, this line may be all that gets read.
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

    private DigestMessage() {
    }

    /**
     * The template for this digest, with its blanks filled.
     *
     * @param digest what to report
     * @param now    the moment of sending; it only dates a digest with nothing in it
     */
    static Template forDigest(DailyDigest digest, Instant now) {
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(now, "now must not be null");

        // A digest with nothing in it covers no period, so it is dated by the day it is sent.
        if (digest.isEmpty()) {
            return new Template(EMPTY_TEMPLATE,
                    List.of(DAY.format(now.atZone(READER_ZONE).toLocalDate())));
        }

        return new Template(TEMPLATE, List.of(
                oneLine(period(digest)),
                String.valueOf(digest.total()),
                String.valueOf(digest.urgent()),
                oneLine(byKind(digest)),
                oneLine(platforms(digest))));
    }

    /**
     * The days the digest covers, as the reader counts them.
     *
     * <p>Usually one day. When a delivery failed, the next digest carries everything still
     * waiting, and the period grows to cover it — "13/09 a 15/09" — so a backlog does not
     * pass for a busy day.
     */
    private static String period(DailyDigest digest) {
        LocalDate first = digest.earliest().atZone(READER_ZONE).toLocalDate();
        LocalDate last = digest.latest().atZone(READER_ZONE).toLocalDate();
        return first.equals(last)
                ? DAY.format(first)
                : DAY.format(first) + " a " + DAY.format(last);
    }

    /**
     * "1 entrevista, 2 recusas", naming only the kinds that occurred. Never empty: this is
     * only called for a digest with something in it, and its counts add up to its total.
     */
    private static String byKind(DailyDigest digest) {
        return KINDS.stream()
                .filter(kind -> digest.countOf(kind.type()) > 0)
                .map(kind -> kind.counted(digest.countOf(kind.type())))
                .collect(Collectors.joining(", "));
    }

    private static String platforms(DailyDigest digest) {
        return digest.platforms().isEmpty() ? NOTHING : String.join(", ", digest.platforms());
    }

    /**
     * Meta refuses a template value that holds a line break, a tab or more than four
     * spaces in a row, and the whole message with it.
     *
     * <p>None of the values above can hold one today: the platform names come from this
     * project's own list. But a refused digest is a day of news that never arrives, so the
     * rule is enforced here instead of trusted.
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
