package com.nathanpaiva.jobtracker.adapters.whatsapp;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.nathanpaiva.jobtracker.domain.DailyDigest;
import com.nathanpaiva.jobtracker.domain.UpdateType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the digest to template translation.
 *
 * <p>No network and no Meta: the digests are built by hand, and the moment of sending is
 * passed in. Times are written in UTC, as the database keeps them; the expected dates are
 * the ones a reader in São Paulo sees, three hours behind.
 */
class DigestMessageTest {

    /** 06:00 in São Paulo on 15/09, the time by which the digest should have arrived. */
    private static final Instant SENT_AT = Instant.parse("2026-09-15T09:00:00Z");

    @Test
    void fillsTheFiveBlanksOfTheDigestTemplateInOrder() {
        DailyDigest digest = digest(
                Map.of(UpdateType.INTERVIEW_INVITE, 1, UpdateType.REJECTION, 2),
                1, List.of("Gupy", "LinkedIn"),
                "2026-09-14T12:00:00Z", "2026-09-14T20:00:00Z");

        DigestMessage.Template template = DigestMessage.forDigest(digest, SENT_AT);

        assertThat(template.name()).isEqualTo("resumo_diario");
        assertThat(template.values()).containsExactly(
                "14/09", "3", "1", "1 entrevista, 2 recusas", "Gupy, LinkedIn");
    }

    /**
     * A day with no news is still reported, through a template of its own: the one for
     * news would end by pointing at a spreadsheet with nothing new in it. It covers no
     * period, so it is dated by the day it is sent.
     */
    @Test
    void reportsADayWithNothingInItThroughTheEmptyTemplate() {
        DigestMessage.Template template = DigestMessage.forDigest(DailyDigest.empty(), SENT_AT);

        assertThat(template.name()).isEqualTo("resumo_diario_vazio");
        assertThat(template.values()).containsExactly("15/09");
    }

    /**
     * When a delivery fails, the next digest carries everything still waiting. The period
     * grows to cover it, so a backlog does not pass for a busy day.
     */
    @Test
    void stretchesThePeriodOverABacklog() {
        DailyDigest digest = digest(Map.of(UpdateType.OTHER, 2), 0, List.of(),
                "2026-09-13T12:00:00Z", "2026-09-15T08:00:00Z");

        assertThat(values(digest).get(0)).isEqualTo("13/09 a 15/09");
    }

    /** A digest whose emails came from no known platform says so instead of a blank. */
    @Test
    void saysNoPlatformInWordsWhenNoneWasTold() {
        DailyDigest digest = digest(Map.of(UpdateType.OTHER, 1), 0, List.of(),
                "2026-09-14T12:00:00Z", "2026-09-14T12:00:00Z");

        assertThat(values(digest).get(4)).isEqualTo("nenhuma");
    }

    /**
     * 02:30 in UTC on the 15th is 23:30 on the 14th in São Paulo. Read in UTC, these two
     * emails would span two days; to the reader they arrived on the same one.
     */
    @Test
    void countsDaysAsTheReaderDoes() {
        DailyDigest digest = digest(Map.of(UpdateType.REJECTION, 2), 0, List.of(),
                "2026-09-14T15:00:00Z", "2026-09-15T02:30:00Z");

        assertThat(values(digest).get(0)).isEqualTo("14/09");
        assertThat(DigestMessage.forDigest(DailyDigest.empty(),
                Instant.parse("2026-09-15T01:00:00Z")).values().get(0)).isEqualTo("14/09");
    }

    @Test
    void namesEachKindInTheSingularAndThePlural() {
        assertThat(byKind(everyKind(1))).isEqualTo("1 proposta, 1 entrevista, 1 teste técnico, "
                + "1 pedido de informação, 1 recusa, 1 confirmação de inscrição, 1 sem categoria");
        assertThat(byKind(everyKind(2))).isEqualTo("2 propostas, 2 entrevistas, 2 testes técnicos, "
                + "2 pedidos de informação, 2 recusas, 2 confirmações de inscrição, 2 sem categoria");
    }

    /**
     * Every kind the classifier can produce must have a name. A kind added to
     * {@link UpdateType} and forgotten here would drop out of the message, and the counts
     * by kind would stop adding up to the total.
     */
    @Test
    void namesEveryKindOfUpdate() {
        assertThat(byKind(everyKind(1)).split(", ")).hasSize(UpdateType.values().length);
    }

    /** What asks something of the reader comes first, whatever order the digest holds. */
    @Test
    void listsWhatAsksSomethingOfTheReaderFirst() {
        assertThat(byKind(Map.of(UpdateType.OTHER, 1, UpdateType.REJECTION, 1,
                UpdateType.OFFER, 1))).isEqualTo("1 proposta, 1 recusa, 1 sem categoria");
    }

    /**
     * Meta refuses a template value that holds a line break, a tab or a run of spaces, and
     * the whole message with it.
     */
    @Test
    void keepsEveryValueOnOneLine() {
        DailyDigest digest = digest(Map.of(UpdateType.OTHER, 1), 0,
                List.of("Gupy\nTalentos", "Sólides\t   Vagas"),
                "2026-09-14T12:00:00Z", "2026-09-14T12:00:00Z");

        assertThat(values(digest).get(4)).isEqualTo("Gupy Talentos, Sólides Vagas");
    }

    private static List<String> values(DailyDigest digest) {
        return DigestMessage.forDigest(digest, SENT_AT).values();
    }

    private static String byKind(Map<UpdateType, Integer> counts) {
        return values(digest(counts, 0, List.of(),
                "2026-09-14T12:00:00Z", "2026-09-14T12:00:00Z")).get(3);
    }

    private static Map<UpdateType, Integer> everyKind(int count) {
        Map<UpdateType, Integer> counts = new EnumMap<>(UpdateType.class);
        for (UpdateType type : UpdateType.values()) {
            counts.put(type, count);
        }
        return counts;
    }

    private static DailyDigest digest(Map<UpdateType, Integer> counts, int urgent,
                                      List<String> platforms, String earliest, String latest) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new DailyDigest(total, counts, urgent, platforms,
                Instant.parse(earliest), Instant.parse(latest), List.of());
    }
}
