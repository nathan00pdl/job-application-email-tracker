package com.nathanpaiva.jobtracker.domain;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingEmailTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-08-27T09:00:00Z");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingGmailMessageId(String invalid) {
        assertThatThrownBy(() -> new IncomingEmail(
                invalid, RECEIVED_AT, "greenhouse.io", "Sua candidatura", "..."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gmailMessageId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingSenderDomain(String invalid) {
        assertThatThrownBy(() -> new IncomingEmail(
                "gmail-id", RECEIVED_AT, invalid, "Sua candidatura", "..."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("senderDomain");
    }

    @Test
    void rejectsANullSubjectOrBody() {
        assertThatThrownBy(() -> new IncomingEmail(
                "gmail-id", RECEIVED_AT, "greenhouse.io", null, "..."))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subject");

        assertThatThrownBy(() -> new IncomingEmail(
                "gmail-id", RECEIVED_AT, "greenhouse.io", "Sua candidatura", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("body");
    }

    /**
     * An empty subject is unusual but real. Refusing one would drop a message instead
     * of protecting anything, so only null is rejected.
     */
    @Test
    void acceptsAnEmptySubjectOrBody() {
        assertThatCode(() -> new IncomingEmail(
                "gmail-id", RECEIVED_AT, "greenhouse.io", "", ""))
                .doesNotThrowAnyException();
    }
}
