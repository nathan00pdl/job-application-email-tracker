package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.EmailClassification;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the digest to template translation.
 *
 * <p>No network and no Meta: the digests are built from classifications written by hand,
 * and the moment of sending is passed in. Times are written in UTC, as the database keeps
 * them; the expected dates are the ones a reader in São Paulo sees, three hours behind.
 */
class DigestMessageTest {

    /** 06:00 in São Paulo on 17/09, the time by which the digest should have arrived. */
    private static final Instant SENT_AT = Instant.parse("2026-09-17T09:00:00Z");

    private static final String SHEET = "sheet-id";
    private static final String SHEET_LINK = "https://docs.google.com/spreadsheets/d/sheet-id/edit";
    private static final String GMAIL = "https://mail.google.com/mail/u/0/#all/";

    private static final Instant SEVENTEENTH = Instant.parse("2026-09-17T12:00:00Z");
    private static final Instant SIXTEENTH = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void fillsTheFifteenBlanksInOrder() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("c1", SEVENTEENTH, UpdateType.APPLICATION_RECEIVED, "indeed.com", "Indeed", false),
                classification("c2", SEVENTEENTH, UpdateType.APPLICATION_RECEIVED, "indeed.com", "Indeed", false),
                classification("r1", SEVENTEENTH, UpdateType.REJECTION, "gupy.com.br", "Gupy", false),
                classification("i1", SEVENTEENTH, UpdateType.INTERVIEW_INVITE, "gupy.com.br", "Gupy", false),
                classification("t1", SIXTEENTH, UpdateType.TECHNICAL_TEST, "empresa.com.br", null, true)));

        DigestMessage.Template template = DigestMessage.forDigest(digest, SENT_AT, SHEET);

        assertThat(template.name()).isEqualTo("resumo_diario_acoes");
        assertThat(template.values()).containsExactly(
                "16/09 a 17/09",
                "5",
                "1 entrevista, 1 teste técnico, 1 recusa, 2 confirmações de inscrição",
                "Entrevista · Gupy · 17/09 · " + GMAIL + "i1",
                "Teste técnico · URGENTE · empresa.com.br · 16/09 · " + GMAIL + "t1",
                "—", "—", "—", "—", "—", "—", "—", "—",
                "nenhum",
                SHEET_LINK);
    }

    /**
     * A day with no news still goes out, in the same shape: the date it was sent, zero, and
     * a dash in every place. A morning without a message still means something failed.
     */
    @Test
    void reportsADayWithNothingInIt() {
        List<String> values = DigestMessage.forDigest(DailyDigest.empty(), SENT_AT, SHEET).values();

        assertThat(values).hasSize(15);
        assertThat(values.subList(0, 3)).containsExactly("17/09", "0", "nenhum");
        assertThat(values.subList(3, 13)).containsOnly("—");
        assertThat(values.subList(13, 15)).containsExactly("nenhum", SHEET_LINK);
    }

    /**
     * A morning of applications and nothing to do: the confirmations are counted, and the
     * places stay empty rather than filling up with them.
     */
    @Test
    void countsConfirmationsWithoutListingThem() {
        DailyDigest digest = DailyDigest.of(IntStream.range(0, 12)
                .mapToObj(n -> classification("c" + n, SEVENTEENTH, UpdateType.APPLICATION_RECEIVED,
                        "indeed.com", "Indeed", false))
                .toList());

        List<String> values = DigestMessage.forDigest(digest, SENT_AT, SHEET).values();

        assertThat(values.get(2)).isEqualTo("12 confirmações de inscrição");
        assertThat(values.subList(3, 13)).containsOnly("—");
        assertThat(values.get(13)).isEqualTo("nenhum");
    }

    /** Ten places, and a count of the rest, so nothing that waits on the reader is silent. */
    @Test
    void countsWhatDoesNotFitInTheTenPlaces() {
        DailyDigest digest = DailyDigest.of(IntStream.range(0, 12)
                .mapToObj(n -> classification("i" + n, SEVENTEENTH, UpdateType.INTERVIEW_INVITE,
                        "gupy.com.br", "Gupy", false))
                .toList());

        List<String> values = DigestMessage.forDigest(digest, SENT_AT, SHEET).values();

        assertThat(values.subList(3, 13)).allMatch(value -> value.startsWith("Entrevista · Gupy"));
        assertThat(values.get(13)).isEqualTo("mais 2, veja a planilha");
    }

    /** The list follows the digest's own order, so the most important is always first. */
    @Test
    void keepsTheMostImportantInTheFirstPlace() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("request", SEVENTEENTH, UpdateType.INFO_REQUEST, "bizneo.com", "Bizneo", false),
                classification("offer", SEVENTEENTH, UpdateType.OFFER, "gupy.com.br", "Gupy", false)));

        List<String> values = DigestMessage.forDigest(digest, SENT_AT, SHEET).values();

        assertThat(values.get(3)).startsWith("Proposta · Gupy").endsWith("offer");
        assertThat(values.get(4)).startsWith("Pedido de informação · Bizneo").endsWith("request");
    }

    /**
     * 02:30 in UTC on the 17th is 23:30 on the 16th in São Paulo. The item and the period
     * say the 16th, as the reader would.
     */
    @Test
    void datesEverythingAsTheReaderDoes() {
        Instant lateOnTheSixteenth = Instant.parse("2026-09-17T02:30:00Z");
        DailyDigest digest = DailyDigest.of(List.of(classification("i1", lateOnTheSixteenth,
                UpdateType.INTERVIEW_INVITE, "gupy.com.br", "Gupy", false)));

        List<String> values = DigestMessage.forDigest(digest, SENT_AT, SHEET).values();

        assertThat(values.get(0)).isEqualTo("16/09");
        assertThat(values.get(3)).contains(" · 16/09 · ");
        assertThat(DigestMessage.forDigest(DailyDigest.empty(),
                Instant.parse("2026-09-17T01:00:00Z"), SHEET).values().get(0)).isEqualTo("16/09");
    }

    @Test
    void namesEachKindInTheSingularAndThePlural() {
        assertThat(byKind(1)).isEqualTo("1 proposta, 1 entrevista, 1 teste técnico, "
                + "1 pedido de informação, 1 recusa, 1 confirmação de inscrição, 1 sem categoria");
        assertThat(byKind(2)).isEqualTo("2 propostas, 2 entrevistas, 2 testes técnicos, "
                + "2 pedidos de informação, 2 recusas, 2 confirmações de inscrição, 2 sem categoria");
    }

    /**
     * Every kind the classifier can produce must have a name. A kind added to
     * {@link UpdateType} and forgotten here would drop out of the counts, and naming an item
     * of that kind would fail.
     */
    @Test
    void namesEveryKindOfUpdate() {
        assertThat(byKind(1).split(", ")).hasSize(UpdateType.values().length);
    }

    /**
     * Meta refuses a template value that holds a line break, a tab or a run of spaces, and
     * the whole message with it.
     */
    @Test
    void keepsEveryValueOnOneLine() {
        DailyDigest digest = DailyDigest.of(List.of(classification("i1", SEVENTEENTH,
                UpdateType.INTERVIEW_INVITE, "gupy.com.br", "Gupy\nTalentos\t   SP", false)));

        assertThat(DigestMessage.forDigest(digest, SENT_AT, SHEET).values().get(3))
                .startsWith("Entrevista · Gupy Talentos SP · ");
    }

    /** The counts blank of a digest holding this many emails of every kind. */
    private static String byKind(int eachKind) {
        List<EmailClassification> classifications = new ArrayList<>();
        for (UpdateType type : UpdateType.values()) {
            for (int n = 0; n < eachKind; n++) {
                classifications.add(classification(type + "-" + n, SEVENTEENTH, type,
                        "gupy.com.br", "Gupy", false));
            }
        }
        return DigestMessage.forDigest(DailyDigest.of(classifications), SENT_AT, SHEET).values().get(2);
    }

    private static EmailClassification classification(String id, Instant receivedAt, UpdateType type,
                                                      String senderDomain, String platform, boolean urgent) {
        return new EmailClassification(id, receivedAt, senderDomain, platform, null, null, type, null, urgent);
    }
}
