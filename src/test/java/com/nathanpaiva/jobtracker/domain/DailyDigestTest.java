package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the digest.
 *
 * <p>No Spring context, no container, no clock. The digest is built from a plain list
 * and decides nothing about time, so there is nothing here to fake.
 */
class DailyDigestTest {

    private static final Instant MORNING = Instant.parse("2026-09-05T09:00:00Z");
    private static final Instant NOON = Instant.parse("2026-09-05T12:00:00Z");
    private static final Instant EVENING = Instant.parse("2026-09-05T20:00:00Z");

    @Test
    void countsHowManyOfEachKindItWasGiven() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.REJECTION, "Greenhouse", false),
                classification("b", NOON, UpdateType.REJECTION, "Gupy", false),
                classification("c", NOON, UpdateType.INTERVIEW_INVITE, "Lever", false)));

        assertThat(digest.total()).isEqualTo(3);
        assertThat(digest.countOf(UpdateType.REJECTION)).isEqualTo(2);
        assertThat(digest.countOf(UpdateType.INTERVIEW_INVITE)).isEqualTo(1);
    }

    /**
     * A message that listed every kind would spend most of itself saying nothing
     * happened. Kinds that did not occur are simply absent, and {@code countOf} answers
     * zero for them.
     */
    @Test
    void holdsOnlyTheKindsThatOccurred() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.OFFER, "Greenhouse", false)));

        assertThat(digest.countsByType()).containsOnlyKeys(UpdateType.OFFER);
        assertThat(digest.countOf(UpdateType.APPLICATION_RECEIVED)).isZero();
    }

    @Test
    void countsTheUrgentOnesSeparately() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.INTERVIEW_INVITE, "Greenhouse", true),
                classification("b", NOON, UpdateType.REJECTION, "Greenhouse", false)));

        assertThat(digest.urgent()).isEqualTo(1);
    }

    /**
     * Sorted and without repeats, so that the same set of emails always produces the
     * same digest. A digest that changed with the order of a database result would be
     * impossible to assert on, and would make the same day look different twice.
     */
    @Test
    void listsEachPlatformOnceInAStableOrder() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.REJECTION, "Lever", false),
                classification("b", NOON, UpdateType.REJECTION, "Greenhouse", false),
                classification("c", NOON, UpdateType.OTHER, "Lever", false)));

        assertThat(digest.platforms()).containsExactly("Greenhouse", "Lever");
    }

    /**
     * The classifier leaves the platform null when the sender domain matches nothing it
     * knows. "Unknown" is not a platform, so it is not reported as one.
     */
    @Test
    void leavesOutClassificationsWhosePlatformIsUnknown() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.OTHER, null, false),
                classification("b", NOON, UpdateType.OTHER, "Gupy", false)));

        assertThat(digest.platforms()).containsExactly("Gupy");
        assertThat(digest.total()).isEqualTo(2);
    }

    @Test
    void readsThePeriodFromTheEmailsItCovers() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.REJECTION, "Greenhouse", false),
                classification("b", MORNING, UpdateType.REJECTION, "Greenhouse", false),
                classification("c", EVENING, UpdateType.OFFER, "Greenhouse", false)));

        assertThat(digest.earliest()).isEqualTo(MORNING);
        assertThat(digest.latest()).isEqualTo(EVENING);
    }

    /**
     * The period is found by comparing arrival times, not by trusting the ends of the
     * list, so the caller is free to sort however it likes.
     */
    @Test
    void findsTheSamePeriodWhateverOrderTheEmailsArriveIn() {
        DailyDigest ascending = DailyDigest.of(List.of(
                classification("a", MORNING, UpdateType.OTHER, "Gupy", false),
                classification("b", EVENING, UpdateType.OTHER, "Gupy", false)));
        DailyDigest descending = DailyDigest.of(List.of(
                classification("b", EVENING, UpdateType.OTHER, "Gupy", false),
                classification("a", MORNING, UpdateType.OTHER, "Gupy", false)));

        assertThat(ascending).isEqualTo(descending);
    }

    /** A day with no news is news, and still produces a digest. */
    @Test
    void summarisesAnEmptyListAsAnEmptyDigest() {
        DailyDigest digest = DailyDigest.of(List.of());

        assertThat(digest.isEmpty()).isTrue();
        assertThat(digest.total()).isZero();
        assertThat(digest.urgent()).isZero();
        assertThat(digest.countsByType()).isEmpty();
        assertThat(digest.platforms()).isEmpty();
        assertThat(digest.earliest()).isNull();
        assertThat(digest.latest()).isNull();
        assertThat(digest).isEqualTo(DailyDigest.empty());
    }

    @Test
    void cannotBeChangedAfterItIsBuilt() {
        DailyDigest digest = DailyDigest.of(List.of(
                classification("a", NOON, UpdateType.OFFER, "Gupy", false)));

        assertThatThrownBy(() -> digest.countsByType().put(UpdateType.OTHER, 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> digest.platforms().add("Lever"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A digest whose counts do not add up to its total would be a lie that no later code
     * could catch, so it cannot be built at all.
     */
    @Test
    void rejectsCountsThatDoNotAddUpToTheTotal() {
        assertThatThrownBy(() -> new DailyDigest(
                5, Map.of(UpdateType.OFFER, 1), 0, List.of(), NOON, NOON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adds up to 1")
                .hasMessageContaining("total is 5");
    }

    @Test
    void rejectsMoreUrgentEmailsThanEmails() {
        assertThatThrownBy(() -> new DailyDigest(
                1, Map.of(UpdateType.OFFER, 1), 2, List.of(), NOON, NOON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("urgent");
    }

    @Test
    void rejectsAKindRecordedAsZero() {
        assertThatThrownBy(() -> new DailyDigest(
                0, Map.of(UpdateType.OFFER, 0), 0, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only the kinds that occurred");
    }

    @Test
    void rejectsAnEmptyDigestThatClaimsToCoverAPeriod() {
        assertThatThrownBy(() -> new DailyDigest(0, Map.of(), 0, List.of(), NOON, NOON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty digest covers no period");
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> new DailyDigest(
                1, Map.of(UpdateType.OFFER, 1), 0, List.of(), EVENING, MORNING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("earliest must not be after latest");
    }

    private static EmailClassification classification(
            String id, Instant receivedAt, UpdateType updateType, String platform, boolean urgent) {
        return new EmailClassification(
                id, receivedAt, "greenhouse.io", platform, null, null, updateType, null, urgent);
    }
}
