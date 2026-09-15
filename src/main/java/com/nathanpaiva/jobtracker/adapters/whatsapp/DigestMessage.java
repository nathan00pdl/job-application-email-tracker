package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.UpdateType;

/**
 * Turns a {@link DailyDigest} into the values of the {@code resumo_diario} template.
 *
 * <p>A WhatsApp message that the business sends first has to be a template Meta approved
 * in advance. Its text is fixed; only the blanks change from one day to the next. This is
 * the approved text, and this class fills its five blanks, in order:
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
 * <p>The two are tied: a change to the template on Meta's side means a change here, and
 * the other way round.
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
     * The reader is in Brazil, so the dates are the reader's dates, in the same zone the
     * spreadsheet writes in. In UTC, an email that arrived at half past nine at night
     * would already belong to the next day.
     */
    private static final ZoneId READER_ZONE = ZoneId.of("America/Sao_Paulo");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    /**
     * What goes in a blank that has nothing to hold, so the message never reads
     * "Plataformas de origem: ."
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
     * The five values, in the order of the template's blanks.
     *
     * @param digest what to report
     * @param now    the moment of sending; it only dates a digest with nothing in it
     */
    static List<String> templateValues(DailyDigest digest, Instant now) {
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return List.of(
                oneLine(period(digest, now)),
                String.valueOf(digest.total()),
                String.valueOf(digest.urgent()),
                oneLine(byKind(digest)),
                oneLine(platforms(digest)));
    }

    /**
     * The days the digest covers, as the reader counts them.
     *
     * <p>Usually one day. When a delivery failed, the next digest carries everything still
     * waiting, and the period stretches to show it — "13/09 a 15/09" — so a backlog does
     * not pass for a busy day.
     *
     * <p>A digest with nothing in it covers no period, so it is dated by the day it is
     * sent.
     */
    private static String period(DailyDigest digest, Instant now) {
        if (digest.isEmpty()) {
            return DAY.format(now.atZone(READER_ZONE).toLocalDate());
        }
        LocalDate first = digest.earliest().atZone(READER_ZONE).toLocalDate();
        LocalDate last = digest.latest().atZone(READER_ZONE).toLocalDate();
        return first.equals(last)
                ? DAY.format(first)
                : DAY.format(first) + " a " + DAY.format(last);
    }

    /** "1 entrevista, 2 recusas", naming only the kinds that occurred. */
    private static String byKind(DailyDigest digest) {
        List<String> counted = KINDS.stream()
                .filter(kind -> digest.countOf(kind.type()) > 0)
                .map(kind -> kind.counted(digest.countOf(kind.type())))
                .toList();
        return counted.isEmpty() ? NOTHING : String.join(", ", counted);
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
