package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the classification decision rule.
 *
 * <p>The rule takes the two signals the pipeline collects for every email — the cheap
 * rule filter and the LLM — and answers two questions: does this email count for the
 * day, and did the two signals disagree?
 *
 * <p>There are only four possible pairs of inputs, so both tests cover all of them.
 * That is the reason to test a rule like this at the domain level: the whole input
 * space fits in a table, and none of it needs a database, a network call or a mock.
 */
class ClassificationDecisionRuleTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-24T10:15:30Z");

    /**
     * The LLM decides on its own. The rule filter is a cheap first pass that picks which
     * emails are worth sending to the LLM, so it never overrules the answer it asked for.
     */
    @ParameterizedTest(name = "rule filter={0}, LLM={1} -> counts for the day: {2}")
    @CsvSource({
            "true,  true,  true",
            "true,  false, false",
            "false, true,  true",
            "false, false, false"
    })
    void followsTheLlmWhenDecidingIfAnEmailCountsForTheDay(
            boolean matchedRuleFilter, boolean llmClassifiedRelevant, boolean expected) {

        EmailClassification classification =
                classificationWith(matchedRuleFilter, llmClassifiedRelevant);

        assertThat(classification.includedInDigest()).isEqualTo(expected);
    }

    /**
     * The flag is on whenever the two signals reached different answers, in either
     * direction. The common case is the first one below: the rule filter matched an
     * email that the LLM then judged unrelated.
     */
    @ParameterizedTest(name = "rule filter={0}, LLM={1} -> disagreement: {2}")
    @CsvSource({
            "true,  false, true",
            "false, true,  true",
            "true,  true,  false",
            "false, false, false"
    })
    void flagsADisagreementWhenTheTwoSignalsDiffer(
            boolean matchedRuleFilter, boolean llmClassifiedRelevant, boolean expected) {

        EmailClassification classification =
                classificationWith(matchedRuleFilter, llmClassifiedRelevant);

        assertThat(classification.hasDisagreement()).isEqualTo(expected);
    }

    private static EmailClassification classificationWith(
            boolean matchedRuleFilter, boolean llmClassifiedRelevant) {

        return new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false, matchedRuleFilter, llmClassifiedRelevant);
    }
}
