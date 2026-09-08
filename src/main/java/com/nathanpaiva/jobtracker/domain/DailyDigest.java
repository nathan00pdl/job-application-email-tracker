package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * What a set of classifications adds up to: how many, of which kinds, from which
 * platforms, and over which stretch of time.
 *
 * <p>Every other domain type here describes one email. This is the first one that looks
 * at many of them at once and answers a different question: not "what is this email"
 * but "what happened".
 *
 * <p><b>It is data, not a message.</b> It holds numbers and names, never a sentence.
 * Turning it into text that a person reads belongs to whoever delivers it, because the
 * wording depends on the channel. If this record returned a ready-made string, the rule
 * for counting would be stuck to the format of one messenger, and testing the counting
 * would mean matching text.
 *
 * <p><b>It carries no text taken from an email</b> — no subject, no body, not even the
 * message id. That is deliberate: a digest is meant to be logged, and the project logs
 * metadata only. Leaving the text out means logging one can never leak the content of a
 * mailbox, rather than relying on the caller to be careful.
 *
 * <p><b>It has no clock and no window.</b> It does not decide which classifications
 * belong to it; it is built from the ones it is handed, and reports the period it
 * actually found in them. The caller decides what to include — today, everything not
 * yet delivered. So the period is an observation, not a setting.
 *
 * <p>An empty digest is normal and valid: a day with no news is news. It covers no
 * period, because there is nothing in it to read a period from.
 *
 * <p><b>Pattern — value object, with a factory method.</b> It has no identity: two
 * digests holding the same numbers are the same digest, which is what {@code record}
 * gives for free. {@link #of(java.util.Collection)} exists because a constructor should
 * not iterate a collection to work out its own arguments — a named factory can do work
 * a constructor should not, and reads as what it does.
 *
 * <p><b>Principle — fail fast.</b> The checks in the compact constructor mean an invalid
 * digest cannot exist at all, so no later code has to ask whether the one it holds makes
 * sense. Validating at the boundary once beats defending everywhere forever.
 *
 * <p>An empty digest is normal and valid: a day with no news is news. It covers no
 * period, because there is nothing in it to read a period from.
 *
 * @param total        how many classifications the digest covers
 * @param countsByType how many of each kind, holding only the kinds that occurred
 * @param urgent       how many of them ask for something time-sensitive
 * @param platforms    the hiring platforms seen, sorted, without repeats; a
 *                     classification whose platform could not be told is left out
 * @param earliest     when the oldest of them arrived; null when the digest is empty
 * @param latest       when the newest of them arrived; null when the digest is empty
 */
public record DailyDigest(
        int total,
        Map<UpdateType, Integer> countsByType,
        int urgent,
        List<String> platforms,
        Instant earliest,
        Instant latest
) {

    /**
     * Checks that the parts agree with each other, and copies the collections so that
     * the digest cannot change after it is built.
     *
     * <p>The checks are here rather than in {@link #of(Collection)} because they must
     * hold for every digest, including one written by hand in a test. A digest whose
     * counts do not add up to its total would be a lie that no later code could catch.
     */
    public DailyDigest {
        Objects.requireNonNull(countsByType, "countsByType must not be null");
        Objects.requireNonNull(platforms, "platforms must not be null");

        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        if (urgent < 0) {
            throw new IllegalArgumentException("urgent must not be negative");
        }
        if (urgent > total) {
            throw new IllegalArgumentException("urgent must not be greater than total");
        }

        Map<UpdateType, Integer> counts = new EnumMap<>(UpdateType.class);
        counts.putAll(countsByType);
        counts.forEach((type, count) -> {
            if (count == null || count <= 0) {
                throw new IllegalArgumentException(
                        "countsByType must hold only the kinds that occurred, but " + type
                                + " is " + count);
            }
        });
        int counted = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (counted != total) {
            throw new IllegalArgumentException(
                    "countsByType adds up to " + counted + ", but total is " + total);
        }
        countsByType = Collections.unmodifiableMap(counts);

        platforms = List.copyOf(platforms);

        if (total == 0) {
            if (earliest != null || latest != null) {
                throw new IllegalArgumentException("an empty digest covers no period");
            }
        } else {
            Objects.requireNonNull(earliest, "earliest must not be null when the digest is not empty");
            Objects.requireNonNull(latest, "latest must not be null when the digest is not empty");
            if (earliest.isAfter(latest)) {
                throw new IllegalArgumentException("earliest must not be after latest");
            }
        }
    }

    /**
     * Adds up the classifications it is given.
     *
     * <p>The order they arrive in does not matter: the period is found by comparing
     * arrival times, not by trusting the first and last of the list. That way the caller
     * is free to sort however it likes, and a change of sort order can never change the
     * digest.
     *
     * @param classifications the classifications to summarise; may be empty, but neither
     *                        it nor anything in it may be null
     */
    public static DailyDigest of(Collection<EmailClassification> classifications) {
        Objects.requireNonNull(classifications, "classifications must not be null");
        if (classifications.isEmpty()) {
            return empty();
        }

        Map<UpdateType, Integer> counts = new EnumMap<>(UpdateType.class);
        Collection<String> platforms = new TreeSet<>();
        int urgent = 0;
        Instant earliest = null;
        Instant latest = null;

        for (EmailClassification classification : classifications) {
            Objects.requireNonNull(classification, "classifications must not contain null");

            counts.merge(classification.updateType(), 1, Integer::sum);

            if (classification.urgent()) {
                urgent++;
            }
            if (classification.platform() != null && !classification.platform().isBlank()) {
                platforms.add(classification.platform());
            }

            Instant receivedAt = classification.receivedAt();
            if (earliest == null || receivedAt.isBefore(earliest)) {
                earliest = receivedAt;
            }
            if (latest == null || receivedAt.isAfter(latest)) {
                latest = receivedAt;
            }
        }

        return new DailyDigest(
                classifications.size(), counts, urgent, List.copyOf(platforms), earliest, latest);
    }

    /** A digest of nothing. A run with no news still produces one. */
    public static DailyDigest empty() {
        return new DailyDigest(0, Map.of(), 0, List.of(), null, null);
    }

    /** Whether there is any news to report. */
    public boolean isEmpty() {
        return total == 0;
    }

    /**
     * How many emails of one kind the digest covers, and zero for a kind that did not
     * occur. Saves every caller from remembering that absent means zero.
     */
    public int countOf(UpdateType type) {
        Objects.requireNonNull(type, "type must not be null");
        return countsByType.getOrDefault(type, 0);
    }
}
