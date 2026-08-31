package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for the domain model.
 *
 * <p>No Spring context, no container, no mocking framework. The model depends on
 * nothing, so these tests run in milliseconds. That is the benefit of keeping the
 * domain free of framework code, and the reason they are kept apart from the
 * integration tests, which need a real database.
 */
class EmailClassificationTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-24T10:15:30Z");

    @Test
    void exposesTheValuesItWasBuiltWith() {
        EmailClassification classification = new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "greenhouse.io", "Greenhouse",
                "Acme Corp", "Backend Engineer", UpdateType.INTERVIEW_INVITE,
                "Convite para entrevista técnica na próxima terça", true);

        assertThat(classification.gmailMessageId()).isEqualTo("18f2a9c3d4e5b6a7");
        assertThat(classification.receivedAt()).isEqualTo(RECEIVED_AT);
        assertThat(classification.senderDomain()).isEqualTo("greenhouse.io");
        assertThat(classification.updateType()).isEqualTo(UpdateType.INTERVIEW_INVITE);
        assertThat(classification.urgent()).isTrue();
    }

    /**
     * The optional fields exist because an email can be saved as "looked at, nothing
     * taken from it". That is the normal result for an email the classifier finds
     * irrelevant, and building one should not force us to invent values.
     */
    @Test
    void acceptsAClassificationWithNothingExtractedFromTheMessage() {
        assertThatCode(() -> new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "newsletter.example.com", null,
                null, null, UpdateType.OTHER, null, false))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingGmailMessageId(String invalid) {
        assertThatThrownBy(() -> new EmailClassification(
                invalid, RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gmailMessageId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingSenderDomain(String invalid) {
        assertThatThrownBy(() -> new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, invalid, null, null, null,
                UpdateType.OTHER, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senderDomain");
    }

    @Test
    void rejectsAMissingReceivedAt() {
        assertThatThrownBy(() -> new EmailClassification(
                "18f2a9c3d4e5b6a7", null, "greenhouse.io", null, null, null,
                UpdateType.OTHER, null, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("receivedAt");
    }

    @Test
    void rejectsAMissingUpdateType() {
        assertThatThrownBy(() -> new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "greenhouse.io", null, null, null,
                null, null, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("updateType");
    }

    /**
     * Comparing by value is not just tidiness. It lets later tests check a whole
     * expected classification in one line, instead of field by field.
     */
    @Test
    void comparesByValueRatherThanByIdentity() {
        EmailClassification one = new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.REJECTION, null, false);
        EmailClassification other = new EmailClassification(
                "18f2a9c3d4e5b6a7", RECEIVED_AT, "greenhouse.io", null, null, null,
                UpdateType.REJECTION, null, false);

        assertThat(one).isEqualTo(other);
        assertThat(one).hasSameHashCodeAs(other);
    }
}
